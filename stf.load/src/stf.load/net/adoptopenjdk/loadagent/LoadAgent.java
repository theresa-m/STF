/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.loadagent;

import java.lang.instrument.*;
import java.security.ProtectionDomain;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.classfile.instruction.ThrowInstruction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.objectweb.asm.Opcodes.*;

class LoadAgent {
    private static final Logger logger = LogManager.getLogger(LoadAgent.class.getName());

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new SystemExitTransformer(), true);
    }

    static class SystemExitTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
            /* Don't transform System.exit in net/adoptopenjdk/loadTest/LoadTest */
            if (className.contains("net/adoptopenjdk/loadTest/LoadTest")) {
                return null;
            }
            // TODO need to look at specific classloader?
            //if (loader != null && loader != ClassLoader.getPlatformClassLoader()) {
            try {
            if (className.contains("ArbitraryJavaTest") && !className.contains("$")) {
                logger.info("TRANSFORM " + className);
                
                ClassReader cr = new ClassReader(classBytes);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                LoadTestClassVisitor ltcv = new LoadTestClassVisitor(cw);
                cr.accept(ltcv, ClassReader.EXPAND_FRAMES);
                byte[] newBytes = cw.toByteArray();

                // TODO debug
                String[] namearr = className.split("/");
                String name = namearr[namearr.length -1];
                try (FileOutputStream stream = new FileOutputStream("/Users/theresamammarella/localdev/stfdebug/" + name + ".class")) {
                    stream.write(newBytes);
                }  catch (IOException e) {
                    e.printStackTrace();
                }

                return newBytes;
            } else {
                return null;
            }
            } catch(Throwable e) {
                logger.info("CAUGHT EXCEPTION " + e.getClass().getName() + " " + e.getMessage());      
            }
            return null;
        }
    }

    public static class LoadTestClassVisitor extends ClassVisitor {
        public LoadTestClassVisitor(ClassVisitor cv) {
            super(ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int methodAccess, String methodName, String methodDesc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(methodAccess, methodName, methodDesc, signature, exceptions);
            return new LoadTestMethodAdapter(methodAccess, methodDesc, methodVisitor);
            // return cv.visitMethod(methodAccess, methodName, methodDesc, signature, exceptions);
        }
    }

    static class LoadTestMethodAdapter extends LocalVariablesSorter {
        private static final Logger logger = LogManager.getLogger(LocalVariablesSorter.class.getName());

        public LoadTestMethodAdapter(int access, String descriptor, MethodVisitor methodVisitor) {
            super(ASM9, access, descriptor, methodVisitor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (isSystemExit(opcode, owner, name, descriptor)) {
                logger.info("TRANSFORM SYSTEM EXIT");
                String blockedExitException = "net/adoptopenjdk/loadTest/BlockedExitException";
                /* The bytecode just before this will have loaded the exit code
                 * onto the stack. Store it in a new local variable so it can
                 * be passed into the new exception.
                 */
                int localId = super.newLocal(Type.INT_TYPE);

                super.visitVarInsn(ISTORE, localId);
                super.visitTypeInsn(NEW, blockedExitException);
                super.visitInsn(DUP);
                super.visitVarInsn(ILOAD, localId);
                super.visitMethodInsn(INVOKESPECIAL, blockedExitException, "<init>", "(I)V");
                super.visitInsn(ATHROW);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        private boolean isSystemExit(int opcode, String owner, String name, String descriptor) {
            return (opcode == INVOKESTATIC)
                && "java/lang/System".equals(owner)
                && "exit".equals(name)
                && "(I)V".equals(descriptor);
        }
    }
}
