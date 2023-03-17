package de.ialistannen.traceaman;

import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.RETURN;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.field.FieldDescription.ForLoadedField;
import net.bytebuddy.description.method.MethodDescription.ForLoadedMethod;
import net.bytebuddy.description.type.TypeDescription.Generic.OfNonGenericType.ForLoadedType;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.dynamic.scaffold.TypeInitializer.None;
import net.bytebuddy.implementation.Implementation.Context.Disabled.Factory;
import net.bytebuddy.implementation.Implementation.Context.ExtractableView;
import net.bytebuddy.implementation.Implementation.Context.FrameGeneration;
import net.bytebuddy.implementation.auxiliary.AuxiliaryType.NamingStrategy.Enumerating;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.StackManipulation.Compound;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.implementation.bytecode.collection.ArrayFactory;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

public class AgentMain {

  public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
    instrumentation.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(ClassLoader loader, String className,
              Class<?> classBeingRedefined,
              ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            try {
              return getBytes(className, classfileBuffer);
            } catch (Throwable t) {
              t.printStackTrace();
              throw new RuntimeException(t);
            }
          }
        }, true
    );
  }

  private static byte[] getBytes(String className, byte[] classfileBuffer) throws Exception {
    if (!className.endsWith("TestClass")) {
      return classfileBuffer;
    }
    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(classfileBuffer);
    classReader.accept(classNode, 0);
    for (MethodNode method : classNode.methods) {
      if (!method.name.equals("foo")) {
        continue;
      }

      for (LocalVariableNode localVariable : method.localVariables) {
        System.out.println(localVariable.name + " " + localVariable.signature);
        System.out.println(
            "  " + localVariable.start.getLabel() + " -> " + localVariable.end.getLabel()
        );
      }
      System.out.println();

      List<LocalVariableNode> liveVariables = new ArrayList<>();
      for (AbstractInsnNode instruction : method.instructions) {
        int op = instruction.getOpcode();
        if ((op >= IRETURN && op <= RETURN) || op == ATHROW) {
          System.out.println(instruction + " is a return");
        }
        if (instruction instanceof LabelNode node) {
          for (LocalVariableNode localVariable : method.localVariables) {
            if (localVariable.start == node) {
              liveVariables.add(localVariable);
            }
          }
          liveVariables.removeIf(it -> it.end == node);
        }
        if (instruction instanceof LineNumberNode node) {
          System.out.println(node.start.getLabel() + " " + node.line);
          System.out.println(liveVariables);
          System.out.println();
          List<StackManipulation> manipulations = new ArrayList<>();

          List<StackManipulation> values = new ArrayList<>();
          for (LocalVariableNode liveVariable : liveVariables) {
            Class<?> type = getClassFromString(Type.getType(liveVariable.desc).getClassName());
            values.add(new Compound(
                new TextConstant(liveVariable.name),
                MethodVariableAccess.of(new ForLoadedType(type)).loadFrom(liveVariable.index),
                Assigner.GENERICS_AWARE.assign(
                    ForLoadedType.of(type), ForLoadedType.of(Object.class), Typing.STATIC
                ),
                MethodInvocation.invoke(new ForLoadedMethod(
                    Map.class.getMethod("entry", Object.class, Object.class)
                ))
            ));
          }
          System.out.println(values.size());
          manipulations.add(FieldAccess.forField(new ForLoadedField(
              System.class.getField("out")
          )).read());
          manipulations.add(Duplication.of(new ForLoadedType(Object.class)));
          manipulations.add(Duplication.of(new ForLoadedType(Object.class)));
          manipulations.add(new TextConstant("Start of line " + node.line));
          manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
              PrintStream.class.getMethod("println", String.class)
          )));
          manipulations.add(
              ArrayFactory.forType(ForLoadedType.of(Object.class))
                  .withValues(values)
          );
          manipulations.add(Assigner.DEFAULT.assign(
              ForLoadedType.of(Object[].class), ForLoadedType.of(Object.class), Typing.STATIC
          ));
          manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
              Arrays.class.getMethod("toString", Object[].class)
          )));
          manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
              PrintStream.class.getMethod("println", Object.class)
          )));
          manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
              PrintStream.class.getMethod("println")
          )));

          Compound compound = new Compound(manipulations);
          ExtractableView context = Factory.INSTANCE.make(
              TargetType.DESCRIPTION, new Enumerating("F"),
              None.INSTANCE, ClassFileVersion.JAVA_V17, ClassFileVersion.JAVA_V17,
              FrameGeneration.DISABLED
          );
          int sizeBefore = method.instructions.size();
          compound.apply(method, context);
          List<AbstractInsnNode> newNodes = new ArrayList<>();
          for (int i = sizeBefore; i < method.instructions.size(); i++) {
            AbstractInsnNode e = method.instructions.get(i);
            newNodes.add(e);
          }
          newNodes.forEach(it -> method.instructions.remove(it));
          AbstractInsnNode current = node;
          for (AbstractInsnNode newNode : newNodes) {
            method.instructions.insert(current, newNode);
            current = newNode;
          }
        }
      }
    }

    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    classNode.accept(new TraceClassVisitor(writer, new PrintWriter(System.err)));

    return writer.toByteArray();
  }

  public static Class<?> getClassFromString(String className) {
    return switch (className) {
      case "byte" -> byte.class;
      case "short" -> short.class;
      case "int" -> int.class;
      case "long" -> long.class;
      case "float" -> float.class;
      case "double" -> double.class;
      case "boolean" -> boolean.class;
      case "void" -> void.class;
      default -> {
        try {
          yield Class.forName(className);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }


}
