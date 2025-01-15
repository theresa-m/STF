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

import java.lang.instrument.ClassFileTransformer;

/* Based on example in https://bugs.openjdk.org/browse/JDK-8338625 */
class LoadAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new LoadTransformer(), true);
    }

    static class LoadTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
            // TODO is this the classloader we want?
            if (loader != null && loader != ClassLoader.getPlatformClassLoader()) {
                return blockSystemExit(classBytes);
            } else {
                return null;
            }
        }

        /*
        * Rewrite every invokestatic of System::exit(int) to an athrow of RuntimeException.
        */
        private static byte[] blockSystemExit(byte[] classBytes) {
            var modified = new AtomicBoolean();
            ClassFile cf = ClassFile.of(ClassFile.DebugElementsOption.DROP_DEBUG);
            ClassModel classModel = cf.parse(classBytes);

            Predicate<MethodModel> invokesSystemExit =
                methodModel -> methodModel.code()
                                        .map(codeModel ->
                                                codeModel.elementStream()
                                                        .anyMatch(LoadAgent::isInvocationOfSystemExit))
                                        .orElse(false);

            CodeTransform rewriteSystemExit =
                (codeBuilder, codeElement) -> {
                    if (isInvocationOfSystemExit(codeElement)) {
                        var runtimeException = ClassDesc.of("java.lang.RuntimeException");
                        codeBuilder.new_(runtimeException)                    
                                .dup()
                                .ldc("System.exit not allowed")
                                .invokespecial(runtimeException,
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"),
                                    false)
                                .athrow();
                        modified.set(true);
                    } else {
                        codeBuilder.with(codeElement);
                    }
                };

            ClassTransform ct = ClassTransform.transformingMethodBodies(invokesSystemExit, rewriteSystemExit);
            byte[] newClassBytes = cf.transform(classModel, ct);
            if (modified.get()) {
                return newClassBytes;
            } else {
                return null;
            }
        }

        private static boolean isInvocationOfSystemExit(CodeElement codeElement) {
            return codeElement instanceof InvokeInstruction i
                    && i.opcode() == Opcode.INVOKESTATIC
                    && "java/lang/System".equals(i.owner().asInternalName())
                    && "exit".equals(i.name().stringValue())
                    && "(I)V".equals(i.type().stringValue());
        }
    }
}