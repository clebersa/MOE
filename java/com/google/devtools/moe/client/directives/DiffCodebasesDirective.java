/*
 * Copyright (c) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.moe.client.directives;

import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.codebase.CodebaseCreationError;
import com.google.devtools.moe.client.parser.Parser;
import com.google.devtools.moe.client.parser.Parser.ParseError;
import com.google.devtools.moe.client.project.ProjectContextFactory;
import com.google.devtools.moe.client.tools.CodebaseDiffer;
import com.google.devtools.moe.client.tools.CodebaseDifference;
import com.google.devtools.moe.client.tools.PatchCodebaseDifferenceRenderer;

import org.kohsuke.args4j.Option;

import javax.inject.Inject;

/**
 * Print the diff of two Codebases.
 */
public class DiffCodebasesDirective extends Directive {
  private static final PatchCodebaseDifferenceRenderer RENDERER =
      new PatchCodebaseDifferenceRenderer();

  @Option(name = "--codebase1", required = true, usage = "Codebase1 expression")
  String codebase1Spec = "";

  @Option(name = "--codebase2", required = true, usage = "Codebase2 expression")
  String codebase2Spec = "";

  private final CodebaseDiffer differ;
  private final Ui ui;

  @Inject
  DiffCodebasesDirective(ProjectContextFactory contextFactory, CodebaseDiffer differ, Ui ui) {
    super(contextFactory); // TODO(cgruber) Inject project context, not its factory
    this.differ = differ;
    this.ui = ui;
  }

  @Override
  protected int performDirectiveBehavior() {
    Codebase codebase1, codebase2;
    try {
      codebase1 = Parser.parseExpression(codebase1Spec).createCodebase(context());
      codebase2 = Parser.parseExpression(codebase2Spec).createCodebase(context());
    } catch (ParseError e) {
      ui.error(e, "Error parsing codebase expression");
      return 1;
    } catch (CodebaseCreationError e) {
      ui.error(e, "Error creating codebase");
      return 1;
    }

    CodebaseDifference diff = differ.diffCodebases(codebase1, codebase2);
    if (diff.areDifferent()) {
      ui.info(
          "Codebases \"%s\" and \"%s\" differ:\n%s", codebase1, codebase2, RENDERER.render(diff));
    } else {
      ui.info("Codebases \"%s\" and \"%s\" are identical", codebase1, codebase2);
    }
    return 0;
  }

  @Override
  public String getDescription() {
    return "Prints the diff output between two codebase expressions";
  }
}
