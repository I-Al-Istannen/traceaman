package de.ialistannen.traceaman;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.dynamic.scaffold.TypeInitializer.None;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.implementation.Implementation.Context.Disabled.Factory;
import net.bytebuddy.implementation.Implementation.Context.ExtractableView;
import net.bytebuddy.implementation.Implementation.Context.FrameGeneration;
import net.bytebuddy.implementation.auxiliary.AuxiliaryType.NamingStrategy.Enumerating;
import net.bytebuddy.implementation.bytecode.Addition;
import net.bytebuddy.implementation.bytecode.StackManipulation.Compound;
import net.bytebuddy.implementation.bytecode.constant.IntegerConstant;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

public class AgentMainBackup {

  public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
    instrumentation.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(ClassLoader loader, String className,
              Class<?> classBeingRedefined,
              ProtectionDomain protectionDomain, byte[] classfileBuffer) {
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
                  Compound compound = new Compound(
                      IntegerConstant.FIVE,
                      Addition.INTEGER
                  );
                  ExtractableView context = Factory.INSTANCE.make(
                      TargetType.DESCRIPTION, new Enumerating("F"),
                      None.INSTANCE, ClassFileVersion.JAVA_V17, ClassFileVersion.JAVA_V17,
                      FrameGeneration.DISABLED
                  );
                  compound.apply(method, context);
                }
              }
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(new TraceClassVisitor(writer, new PrintWriter(System.err)));

            return writer.toByteArray();
          }
        }, true
    );
//    new AgentBuilder.Default()
//        // Otherwise the instrumentation swallows any error, which makes things hard to debug
//        .with(new LoggingListener())
//        // We want to retransform classes and the JVM does not support hotswap with schema changes
//        .disableClassFormatChanges()
//        .with(RedefinitionStrategy.RETRANSFORMATION)
//        // Only re-transform types we either record or record/mock as nested invocations
//        .type(nameEndsWith("TestClass"))
//        .transform(
//            (builder, typeDescription, classLoader, module, protectionDomain) -> builder.visit(
//                new ForDeclaredMethods()
//                    .writerFlags(ClassWriter.COMPUTE_FRAMES)
//                    .method(named("foo"), new MyWrapper())
//            )
//        )
//        .transform(
//            (builder, typeDescription, classLoader, module, protectionDomain) -> {
//              return new
//            }
//        )
//        .installOn(instrumentation);
  }

  private static List<LocalVariableNode> getLocalVariables(byte[] classfileBuffer) {
    ClassNode classNode = new ClassNode();
    ClassReader classReader = new ClassReader(classfileBuffer);
    classReader.accept(classNode, 0);
    List<LocalVariableNode> localVariables = new ArrayList<>();
    for (MethodNode method : classNode.methods) {
      if (!method.name.equals("foo")) {
        continue;
      }
      localVariables.addAll(method.localVariables);
    }
    return localVariables;
  }

  private static class CollectVariablesWrapper implements MethodVisitorWrapper {

    @Override
    public MethodVisitor wrap(TypeDescription instrumentedType,
        MethodDescription instrumentedMethod, MethodVisitor methodVisitor,
        Context implementationContext, TypePool typePool, int writerFlags, int readerFlags) {
      return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
            Label start, Label end, int index) {
          super.visitLocalVariable(name, descriptor, signature, start, end, index);
          System.out.println("CollectVariablesWrapper.visitLocalVariable");
          System.out.println(
              "name = " + name + ", descriptor = " + descriptor + ", signature = " + signature
              + ", start = " + start + ", end = " + end + ", index = " + index);
          System.out.println();
        }
      };
    }
  }

  private static class MyWrapper implements MethodVisitorWrapper {

    @Override
    public MethodVisitor wrap(
        TypeDescription instrumentedType,
        MethodDescription instrumentedMethod, MethodVisitor methodVisitor,
        Context implementationContext, TypePool typePool, int writerFlags,
        int readerFlags) {
      return new MethodVisitor(Opcodes.ASM9, methodVisitor) {

        @Override
        public void visitLineNumber(int line, Label start) {
          System.out.println("MyWrapper.visitLineNumber");
          System.out.println("line = " + line + ", start = " + start);
          System.out.println();
          super.visitLineNumber(line, start);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
            boolean isInterface) {
          System.out.println("MyWrapper.visitMethodInsn");
          super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
          System.out.println("MyWrapper.visitIntInsn");
          super.visitIntInsn(opcode, operand);
        }
      };
    }

  }

  private static class LoggingListener implements Listener {

    @Override
    public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module,
        boolean loaded) {
    }

    @Override
    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader,
        JavaModule module, boolean loaded, DynamicType dynamicType) {
      System.out.println("AgentMain.onTransformation");
      System.out.println(
          "  typeDescription = \033[36m" + typeDescription + "\033[0m, classLoader = " + classLoader
          + ", module = " + module + ", loaded = " + loaded + ", dynamicType = "
          + dynamicType
      );
      String target = "spoon.reflect.visitor.LexicalScopeScanner$1Visitor";
      if (target.endsWith(typeDescription.getTypeName())) {
        try {
          Files.write(Path.of("/tmp/Foo.class"), dynamicType.getBytes());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader,
        JavaModule module, boolean loaded) {
    }

    @Override
    public void onError(String typeName, ClassLoader classLoader, JavaModule module,
        boolean loaded, Throwable throwable) {
      System.out.println("AgentMain.onError");
      System.out.println(
          "\033[31m  typeName = " + typeName + ", classLoader = " + classLoader + ", module = "
          + module
          + ", loaded = " + loaded + ", throwable = " + throwable
          + "\033[0m");
    }

    @Override
    public void onComplete(String typeName, ClassLoader classLoader, JavaModule module,
        boolean loaded) {
    }
  }

}
