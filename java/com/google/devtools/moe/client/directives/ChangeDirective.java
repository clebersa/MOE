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
import com.google.devtools.moe.client.Task;
import com.google.devtools.moe.client.codebase.Codebase;
import com.google.devtools.moe.client.codebase.CodebaseCreationError;
import com.google.devtools.moe.client.parser.Parser;
import com.google.devtools.moe.client.parser.Parser.ParseError;
import com.google.devtools.moe.client.project.ProjectContextFactory;
import com.google.devtools.moe.client.writer.DraftRevision;
import com.google.devtools.moe.client.writer.Writer;
import com.google.devtools.moe.client.writer.WritingError;

import org.kohsuke.args4j.Option;

import javax.inject.Inject;

/**
 * Create a Change in a source control system using command line flags.
 */
public class ChangeDirective extends Directive {
  @Option(name = "--codebase", required = true, usage = "Codebase expression to evaluate")
  String codebase = "";

  @Option(name = "--destination", required = true, usage = "Expression of destination writer")
  String destination = "";

  private final Ui ui;
  private final DraftRevision.Factory revisionFactory;

  @Inject
  ChangeDirective(
      ProjectContextFactory contextFactory, Ui ui, DraftRevision.Factory revisionFactory) {
    super(contextFactory); // TODO(cgruber) Inject project context, not its factory
    this.ui = ui;
    this.revisionFactory = revisionFactory;
  }

  @Override
  protected int performDirectiveBehavior() {
    Task changeTask =
        ui.pushTask(
            "create_change",
            "Creating a change in \"%s\" with contents \"%s\"",
            destination,
            codebase);

    Codebase c;
    try {
      c = Parser.parseExpression(codebase).createCodebase(context());
    } catch (ParseError e) {
      ui.error(e, "Error parsing codebase");
      return 1;
    } catch (CodebaseCreationError e) {
      ui.error(e, "Error creating codebase");
      return 1;
    }

    Writer writer;
    try {
      writer = Parser.parseRepositoryExpression(destination).createWriter(context());
    } catch (ParseError e) {
      ui.error(e, "Error parsing change destination");
      return 1;
    } catch (WritingError e) {
      ui.error(e, "Error writing change");
      return 1;
    }

    DraftRevision r = revisionFactory.create(c, writer, null);
    if (r == null) {
      return 1;
    }

    ui.popTaskAndPersist(changeTask, writer.getRoot());
    return 0;
  }

  @Override
  public String getDescription() {
    return "Creates a (pending) change";
  }

}
