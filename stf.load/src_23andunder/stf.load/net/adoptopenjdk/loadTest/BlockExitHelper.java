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

package net.adoptopenjdk.loadTest;

import java.security.Permission;

/* Setup a security manager to block System.exit attempts */
class BlockExitHelper {
    SecurityManager defaultSecurityManager;

    public BlockExitHelper() {
        this.defaultSecurityManager = System.getSecurityManager();
    }

    void enable() {
        System.setSecurityManager(new SecurityManager() { 
            @Override
            public void checkExit(int status) {
                // Don't allow the test to exit the process
                super.checkExit(status);
                throw new BlockedExitException(status);
            }

            public void checkPermission(Permission perm) {
            }

            // Don't block permission check, so that log4j will work
            public void checkPermission(Permission perm, Object context) {
            }
        });
    }

    void disable() {
        System.setSecurityManager(defaultSecurityManager);
    }
}
