package de.ialistannen.traceaman;

import de.ialistannen.traceaman.util.LocalVariable;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.method.MethodDescription.ForLoadedConstructor;
import net.bytebuddy.description.method.MethodDescription.ForLoadedMethod;
import net.bytebuddy.description.type.TypeDescription;
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
import net.bytebuddy.implementation.bytecode.TypeCreation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.implementation.bytecode.collection.ArrayFactory;
import net.bytebuddy.implementation.bytecode.constant.ClassConstant;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.implementation.bytecode.constant.NullConstant;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
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

  private static final ExtractableView BYTE_BUDDY_CONTEXT = Factory.INSTANCE.make(
      TargetType.DESCRIPTION, new Enumerating("F"),
      None.INSTANCE, ClassFileVersion.JAVA_V17, ClassFileVersion.JAVA_V17,
      FrameGeneration.DISABLED
  );

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
          StackManipulation callLineLog = getCallToLineLogMethod(
              className, method, liveVariables, node
          );
          applyStackManipulation(method, node, callLineLog);
        }
      }
    }

    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    classNode.accept(new TraceClassVisitor(writer, new PrintWriter(System.err)));

    return writer.toByteArray();
  }

  private static StackManipulation getCallToLineLogMethod(
      String className,
      MethodNode method,
      List<LocalVariableNode> liveVariables,
      LineNumberNode node
  ) throws NoSuchMethodException {
    List<StackManipulation> manipulations = new ArrayList<>();

    List<StackManipulation> values = new ArrayList<>();
    for (LocalVariableNode liveVariable : liveVariables) {
      Class<?> type = getClassFromString(Type.getType(liveVariable.desc).getClassName());
      values.add(new Compound(List.of(
          // new LocalVariable(
          TypeCreation.of(TypeDescription.ForLoadedType.of(LocalVariable.class)),
          Duplication.of(TypeDescription.ForLoadedType.of(LocalVariable.class)),
          //   String name
          new TextConstant(liveVariable.name),
          // , Class<?> type
          ClassConstant.of(TypeDescription.ForLoadedType.of(type)),
          // , Object value
          MethodVariableAccess.of(ForLoadedType.of(type)).loadFrom(liveVariable.index),
          Assigner.GENERICS_AWARE.assign(
              ForLoadedType.of(type), ForLoadedType.of(Object.class), Typing.STATIC
          ),
          // );
          MethodInvocation.invoke(new ForLoadedConstructor(
              LocalVariable.class.getConstructor(String.class, Class.class, Object.class)
          ))
      )));
    }
    // Stack is empty.

    //  public static void log(
    //    String file,
    manipulations.add(new TextConstant(className));
    //    int lineNumber,
    manipulations.add(IntegerConstant.forValue(node.line));
    //    Object receiver,
    if (Modifier.isStatic(method.access)) {
      manipulations.add(NullConstant.INSTANCE);
    } else {
      manipulations.add(MethodVariableAccess.loadThis());
    }
    //    LocalVariable[] localVariables
    manipulations.add(
        ArrayFactory.forType(ForLoadedType.of(LocalVariable.class))
            .withValues(values)
    );
    //  );
    manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
        ContextCollector.class.getMethod(
            "log",
            String.class, int.class, Object.class, LocalVariable[].class
        )
    )));

    return new Compound(manipulations);
  }

  /**
   * Applies a stack manipulation and adds the resulting instructions.
   *
   * @param method the method to add it to
   * @param insertAfter the node to insert it after
   * @param manipulation the stack manipulation to apply
   * @return the new current instruction
   */
  private static AbstractInsnNode applyStackManipulation(
      MethodNode method,
      AbstractInsnNode insertAfter,
      StackManipulation manipulation
  ) {
    int sizeBefore = method.instructions.size();

    manipulation.apply(method, BYTE_BUDDY_CONTEXT);

    List<AbstractInsnNode> newNodes = new ArrayList<>();
    for (int i = sizeBefore; i < method.instructions.size(); i++) {
      AbstractInsnNode e = method.instructions.get(i);
      newNodes.add(e);
    }
    newNodes.forEach(it -> method.instructions.remove(it));

    AbstractInsnNode current = insertAfter;
    for (AbstractInsnNode newNode : newNodes) {
      method.instructions.insert(current, newNode);
      current = newNode;
    }

    return current;
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
