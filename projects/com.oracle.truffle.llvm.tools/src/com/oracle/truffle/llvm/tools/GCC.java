/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tools;

import java.io.File;
import java.io.IOException;

import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;

public final class GCC extends CompilerBase {

    private static final File GPP_PATH = Mx.executeGetGCCProgramPath("g++");
    private static final File GFORTRAN_PATH = Mx.executeGetGCCProgramPath("gfortran");
    private static final File GCC_PATH = Mx.executeGetGCCProgramPath("gcc");

    private GCC() {
    }

    public static void compileObjectToMachineCode(File objectFile, File executable) {
        String linkCommand = GCC_PATH + " " + objectFile.getAbsolutePath() + " -o " + executable.getAbsolutePath() + " -lm -lgfortran -lgmp";
        ProcessUtil.executeNativeCommandZeroReturn(linkCommand);
        executable.setExecutable(true);
    }

    public static void compileToLLVMIR(File toBeCompiled, File destinationFile) {
        String tool;
        if (ProgrammingLanguage.C.isFile(toBeCompiled)) {
            tool = GCC_PATH + " -std=gnu99";
        } else if (ProgrammingLanguage.FORTRAN.isFile(toBeCompiled)) {
            tool = GFORTRAN_PATH.toString();
        } else if (ProgrammingLanguage.C_PLUS_PLUS.isFile(toBeCompiled)) {
            tool = GPP_PATH.toString();
        } else {
            throw new AssertionError(toBeCompiled);
        }

        String destinationLLFileName = destinationFile.getAbsolutePath();
        File interimFile;
        try {
            interimFile = File.createTempFile("interim", ".ll");
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        if (destinationFile.getName().endsWith(".bc")) {
            destinationLLFileName = interimFile.getAbsolutePath();
        }

        String[] llFileGenerationCommand = new String[]{tool, "-I " + LLVMOptions.ENGINE.projectRoot() + "/../include", "-S", dragonEggOption(),
                        "-fplugin-arg-dragonegg-emit-ir", "-o " + destinationLLFileName, toBeCompiled.getAbsolutePath()};
        ProcessUtil.executeNativeCommandZeroReturn(llFileGenerationCommand);

        // Converting interim .ll file to .bc file
        if (destinationFile.getName().endsWith(".bc")) {
            LLVMAssembler.assembleToBitcodeFile(interimFile, destinationFile);
        }
    }

    private static String dragonEggOption() {
        return "-fplugin=" + LLVMToolPaths.LLVM_DRAGONEGG;
    }

    public static ProgrammingLanguage[] getSupportedLanguages() {
        return new ProgrammingLanguage[]{ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.FORTRAN};
    }
}
