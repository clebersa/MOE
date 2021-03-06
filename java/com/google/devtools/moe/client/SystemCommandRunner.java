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

package com.google.devtools.moe.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import dagger.Provides;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An implementation of {@link CommandRunner} that executes external programs via the
 * standard java ProcessBuilder, capturing console and error output and return status.
 */
@Singleton
public class SystemCommandRunner implements CommandRunner {
  private final Ui ui;

  @Inject
  public SystemCommandRunner(Ui ui) {
    this.ui = ui;
  }

  @Override
  public CommandOutput runCommandWithFullOutput(
      String cmd, List<String> args, String workingDirectory) throws CommandException {
    ImmutableList<String> cmdArgs =
        new ImmutableList.Builder<String>().add(cmd).addAll(args).build();

    ui.debug("%s $ %s", workingDirectory, Joiner.on(" ").join(cmdArgs));

    ProcessBuilder pb = new ProcessBuilder(cmdArgs);
    if (workingDirectory != null && !workingDirectory.isEmpty()) {
      pb.directory(new File(workingDirectory));
    }
    Process p;
    int returnStatus;
    String stdoutData, stderrData;
    try {
      p = pb.start();
      p.getOutputStream().close();
      // We need to read data from the output steams.
      // Why? Because if we don't read from them, then the process we have started will fill the
      // buffers and block. We will be in a deadlock.
      // If there were only one stream, we could just do repeated calls to read() until we got
      // EOF.
      // But because there are two streams (both stdout and stderr), we have to read from each.
      // read() is blocking, so we use available().

      Sink stdoutSink = new Sink(p.getInputStream());
      Sink stderrSink = new Sink(p.getErrorStream());

      // Sleep in longer increments when it's not generating output.
      // When it is, reset this value.
      int timeToSleep = 1;
      while (true) {
        while (stdoutSink.isAvailable()) {
          if (stdoutSink.consumeByte()) {
            timeToSleep = 1;
          } else {
            stdoutSink.closeStream();
          }
        }
        while (stderrSink.isAvailable()) {
          if (stderrSink.consumeByte()) {
            timeToSleep = 1;
          } else {
            stderrSink.closeStream();
          }
        }
        try {
          returnStatus = p.exitValue();
          break;
        } catch (IllegalThreadStateException expected) {
        }
        timeToSleep++;
        // Never sleep more than half a second.
        if (timeToSleep > 500) {
          timeToSleep = 500;
        }
        Thread.sleep(timeToSleep);
      }
      if (stdoutSink.isAvailable()) {
        while (stdoutSink.consumeByte()) {}
      }
      if (stderrSink.isAvailable()) {
        while (stderrSink.consumeByte()) {}
      }

      stdoutData = stdoutSink.getData();
      stderrData = stderrSink.getData();
    } catch (IOException e) {
      throw new MoeProblem("Cannot run process: %s", e.getMessage());
    } catch (InterruptedException e) {
      throw new MoeProblem("Interrupted while running process: %s", cmdArgs);
    }
    if (returnStatus == 0) {
      return new CommandOutput(stdoutData, stderrData);
    }
    throw new CommandException(cmd, args, stdoutData, stderrData, returnStatus);
  }

  @Override
  public String runCommand(String cmd, List<String> args, String workingDirectory)
      throws CommandException {
    return runCommandWithFullOutput(cmd, args, workingDirectory).getStdout();
  }

  /**
   * Class responsible to manager the use of streams by the 
   * {@link SystemCommandRunner} class.
   */
  private static class Sink {
    private final List<Byte> bytes = Lists.newArrayList();
    private InputStream stream;

    Sink(InputStream stream) {
      this.stream = stream;
    }

    /**
     * Checks if a stream is available to be read.
     * 
     * @return true if the stream is available or false, otherwise.
     * 
     * @throws IOException if some error occurs during the checking.
     */
    boolean isAvailable() throws IOException {
      return stream != null && stream.available() > 0;
    }

    /**
     * Closes the stream. It is the same of making stream equals to null.
     */
    void closeStream() {
      stream = null;
    }

    /**
     * Reads the next byte from the stream to the {@link #bytes} list.
     * 
     * @return true is the byte was read successfully or false if there was no 
     * more byte to read.
     * 
     * @throws IOException if some error occurs when reading the byte.
     */
    boolean consumeByte() throws IOException {
      int data = stream.read();
      if (data == -1) {
        return false;
      } else {
        bytes.add((byte) data);
        return true;
      }
    }

    /**
     * Gets the bytes read from the stream as a string.
     * 
     * @return the string represented by the bytes read from the stream.
     */
    String getData() {
      byte[] byteArray = new byte[bytes.size()];
      int i = 0;
      for (Byte b : bytes) {
        byteArray[i++] = b;
      }
      return new String(byteArray);
    }
  }

  /** 
   * A Dagger module for binding this implementation of {@link CommandRunner}.
   */
  @dagger.Module
  public static class Module {
    @Provides
    public CommandRunner runner(SystemCommandRunner impl) {
      return impl;
    }
  }
}
