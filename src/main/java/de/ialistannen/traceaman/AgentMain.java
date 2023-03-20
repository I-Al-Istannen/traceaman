package de.ialistannen.traceaman;

import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.RETURN;

import de.ialistannen.traceaman.util.ByteBuddyHelper;
import de.ialistannen.traceaman.util.ByteBuddyHelper.InsertPosition;
import de.ialistannen.traceaman.util.Classes;
import de.ialistannen.traceaman.util.LocalVariable;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.description.method.MethodDescription.ForLoadedConstructor;
import net.bytebuddy.description.method.MethodDescription.ForLoadedMethod;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic;
import net.bytebuddy.description.type.TypeDescription.Generic.OfNonGenericType.ForLoadedType;
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

  public static void premain(String arguments, Instrumentation instrumentation) {
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

      List<LocalVariableNode> liveVariables = new ArrayList<>();
      for (AbstractInsnNode instruction : method.instructions) {
        AbstractInsnNode currentNode = instruction;

        if (instruction instanceof LabelNode node) {
          for (LocalVariableNode localVariable : method.localVariables) {
            if (localVariable.start == node) {
              liveVariables.add(localVariable);
            }
          }
          liveVariables.removeIf(it -> it.end == node);
        }
        if (instruction instanceof LineNumberNode node) {
          StackManipulation callLineLog = getCallToLineLogMethod(
              className, method, liveVariables, node
          );
          currentNode = ByteBuddyHelper.applyStackManipulation(
              method, node, callLineLog, InsertPosition.AFTER
          );
        }

        int opcode = instruction.getOpcode();
        if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
          StackManipulation callLineLog = getCallToReturnLogMethod(className, method);
          currentNode = ByteBuddyHelper.applyStackManipulation(
              method, currentNode, callLineLog, InsertPosition.BEFORE
          );
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
      // Skip "this" object
      if (liveVariable.index == 0 && !Modifier.isStatic(method.access)) {
        continue;
      }
      Class<?> type = Classes.getClassFromString(Type.getType(liveVariable.desc).getClassName());
      values.add(createLocalVariable(liveVariable.name, liveVariable.index, type));
    }

    // Stack is still empty.

    //  public static void logLine(
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
            "logLine",
            String.class, int.class, Object.class, LocalVariable[].class
        )
    )));

    return new Compound(manipulations);
  }

  private static Compound createLocalVariable(String name, int readIndex, Class<?> type)
      throws NoSuchMethodException {
    return new Compound(List.of(
        // new LocalVariable(
        TypeCreation.of(TypeDescription.ForLoadedType.of(LocalVariable.class)),
        Duplication.of(TypeDescription.ForLoadedType.of(LocalVariable.class)),
        //   String name
        new TextConstant(name),
        // , Class<?> type
        ClassConstant.of(TypeDescription.ForLoadedType.of(type)),
        // , Object value
        MethodVariableAccess.of(ForLoadedType.of(type)).loadFrom(readIndex),
        Assigner.GENERICS_AWARE.assign(
            ForLoadedType.of(type), ForLoadedType.of(Object.class), Typing.STATIC
        ),
        // );
        MethodInvocation.invoke(new ForLoadedConstructor(
            LocalVariable.class.getConstructor(String.class, Class.class, Object.class)
        ))
    ));
  }

  private static StackManipulation getCallToReturnLogMethod(
      String className,
      MethodNode method
  ) throws NoSuchMethodException {
    List<StackManipulation> manipulations = new ArrayList<>();

    // Stack is empty.

    // public static void logReturn(
    //   Object returnValue,
    Generic typeDesc = ForLoadedType.of(
        Classes.getClassFromString(Type.getMethodType(method.desc).getReturnType().getClassName())
    );
    manipulations.add(Duplication.of(typeDesc));
    manipulations.add(Assigner.GENERICS_AWARE.assign(
        typeDesc, ForLoadedType.of(Object.class), Typing.STATIC
    ));
    //   String className
    manipulations.add(new TextConstant(className));
    //  );
    manipulations.add(MethodInvocation.invoke(new ForLoadedMethod(
        ContextCollector.class.getMethod(
            "logReturn", Object.class, String.class
        )
    )));

    return new Compound(manipulations);
  }

}
