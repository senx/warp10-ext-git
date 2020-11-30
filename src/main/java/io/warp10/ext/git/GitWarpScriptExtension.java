//
//   Copyright 2020  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.ext.git;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import io.warp10.WarpConfig;
import io.warp10.warp.sdk.WarpScriptExtension;

public class GitWarpScriptExtension extends WarpScriptExtension {

  private static Map<String,Object> functions;

  /**
   * Root directory where all allowed repos will reside, possibly as symbolic links
   * to actual location.
   */
  public static final String CONF_ROOT = "git.root";

  public static final String GITCOMMIT = "GITCOMMIT";

  public static final String CAP_GITREPO = "git.repo";
  public static final String CAP_GITNAME = "git.name";
  public static final String CAP_GITEMAIL = "git.email";

  private static final File ROOT;

  static {

    String root = WarpConfig.getProperty(CONF_ROOT);

    if (null != root) {
      File froot = new File(root);
      if (!froot.exists()) {
        throw new RuntimeException("Configured '" + CONF_ROOT + "' (" + root + ") does not exist.");
      }
      ROOT = froot;
    } else {
      ROOT = null;
    }

    functions = new HashMap<String,Object>();

    functions.put(GITCOMMIT, new GITCOMMIT(GITCOMMIT));
  }
  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }

  public static File getRoot() {
    return ROOT;
  }
}