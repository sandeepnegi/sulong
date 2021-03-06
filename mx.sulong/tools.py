from __future__ import print_function

import fnmatch
import mx
import os

import mx_sulong

class ProgrammingLanguage(object):
    class PL(object):
        def __init__(self, name, exts):
            self.name = name
            self.exts = exts

    exts = {}

    @staticmethod
    def register(name, *exts):
        lang = ProgrammingLanguage.PL(name, exts)
        setattr(ProgrammingLanguage, name, lang)
        for ext in exts:
            ProgrammingLanguage.exts[ext] = lang

    @staticmethod
    def lookup(extension):
        return ProgrammingLanguage.exts.get(extension, None)

    @staticmethod
    def lookupFile(f):
        _, ext = os.path.splitext(f)
        return ProgrammingLanguage.lookup(ext[1:])

ProgrammingLanguage.register('FORTRAN', 'f90', 'f', 'f03')
ProgrammingLanguage.register('C', 'c')
ProgrammingLanguage.register('C_PLUS_PLUS', 'cpp', 'cc', 'C')
ProgrammingLanguage.register('OBJECTIVE_C', 'm')
ProgrammingLanguage.register('LLVMIR', 'll')
ProgrammingLanguage.register('LLVMBC', 'bc')
ProgrammingLanguage.register('LLVMSU', 'su')
ProgrammingLanguage.register('EXEC', 'out')


class Optimization(object):
    class Opt(object):
        def __init__(self, name, flags):
            self.name = name
            self.flags = flags

    @staticmethod
    def register(name, *flags):
        setattr(Optimization, name, Optimization.Opt(name, list(flags)))

Optimization.register('NONE')
Optimization.register('O1', '-O1')
Optimization.register('O2', '-O2')
Optimization.register('O3', '-O3')


class Tool(object):
    def supports(self, language):
        return language in self.supportedLanguages

    def runTool(self, args, errorMsg=None):
        try:
            if not mx.get_opts().verbose:
                f = open(os.devnull, 'w')
                ret = mx.run(args, out=f, err=f)
            else:
                f = None
                ret = mx.run(args)
        except SystemExit:
            ret = -1
            if errorMsg is None:
                print('\nError: Cannot run %s' % args)
            else:
                print('\nError: %s' % errorMsg)
        if f is not None:
            f.close()
        return ret

class ClangCompiler(Tool):
    def __init__(self):
        self.name = 'clang'
        self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.OBJECTIVE_C]

    def getTool(self, inputFile):
        inputLanguage = ProgrammingLanguage.lookupFile(inputFile)
        if inputLanguage == ProgrammingLanguage.C or inputLanguage == ProgrammingLanguage.OBJECTIVE_C:
            return 'clang'
        elif inputLanguage == ProgrammingLanguage.C_PLUS_PLUS:
            return 'clang++'
        else:
            raise Exception('Unsupported input language')

    def run(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool), '-c', '-S', '-emit-llvm', '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

    def compileReferenceFile(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool), '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

class GCCCompiler(Tool):
    def __init__(self):
        self.name = 'gcc'
        self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.FORTRAN]

    def run(self, inputFile, outputFile, flags):
        inputLanguage = ProgrammingLanguage.lookupFile(inputFile)
        if inputLanguage == ProgrammingLanguage.C:
            tool = mx_sulong.getGCC()
            flags.append('-std=gnu99')
        elif inputLanguage == ProgrammingLanguage.C_PLUS_PLUS:
            tool = mx_sulong.getGPP()
        elif inputLanguage == ProgrammingLanguage.FORTRAN:
            tool = mx_sulong.getGFortran()
        else:
            raise Exception('Unsupported input language')

        return self.runTool([tool, '-S', '-fplugin=' + mx_sulong._dragonEggPath, '-fplugin-arg-dragonegg-emit-ir', '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, os.path.basename(tool)))

class Opt(Tool):
    def __init__(self, name, passes):
        self.name = name
        self.supportedLanguages = [ProgrammingLanguage.LLVMIR]
        self.passes = passes

    def run(self, inputFile, outputFile, flags):
        return mx.run([mx_sulong.findLLVMProgram('opt'), '-S', '-o', outputFile] + self.passes + [inputFile])

Tool.CLANG = ClangCompiler()
Tool.GCC = GCCCompiler()
Tool.BB_VECTORIZE = Opt('BB_VECTORIZE', ['-functionattrs', '-instcombine', '-always-inline', '-jump-threading', '-simplifycfg', '-mem2reg', '-scalarrepl', '-bb-vectorize'])


def createOutputPath(inputFile, outputDir):
    base, _ = os.path.splitext(inputFile)
    outputPath = os.path.join(outputDir, os.path.relpath(base))
    # ensure that there is one folder for each testfile
    outputPath = os.path.join(outputPath, os.path.basename(base))

    outputFolder = os.path.dirname(outputPath)
    if not os.path.exists(outputFolder):
        os.makedirs(outputFolder)

    return outputPath

def getOutputName(inputFile, outputDir, tool, optimization, target):
    outputPath = createOutputPath(inputFile, outputDir)
    return '%s_%s_%s.%s' % (outputPath, tool.name, optimization.name, target.exts[0])

def getReferenceName(inputFile, outputDir, target):
    outputPath = createOutputPath(inputFile, outputDir)
    return '%s.%s' % (outputPath, target.exts[0])

def isFileUpToDate(inputFile, outputFile):
    return os.path.exists(outputFile) and os.path.getmtime(inputFile) < os.path.getmtime(outputFile)

def collectExcludes(path):
    for root, _, files in os.walk(path):
        for f in files:
            if f.endswith('.exclude'):
                for line in open(os.path.join(root, f)):
                    yield line.strip()

def findRecursively(path, excludes=None):
    if excludes is None:
        excludes = []
    for root, _, files in os.walk(path):
        for f in files:
            if ProgrammingLanguage.lookupFile(f) is not None:
                absFilePath = os.path.join(root, f)
                relFilePath = os.path.relpath(absFilePath, path)
                if not matches(relFilePath, excludes):
                    yield absFilePath

def matches(path, patterns):
    return any(fnmatch.fnmatch(path, p) for p in list(patterns))

def multicompileFile(inputFile, outputDir, tools, flags, optimizations, target, optimizers=None):
    if optimizers is None:
        optimizers = []
    lang = ProgrammingLanguage.lookupFile(inputFile)
    for tool in tools:
        if tool.supports(lang):
            for optimization in optimizations:
                outputFile = getOutputName(inputFile, outputDir, tool, optimization, target)
                if not isFileUpToDate(inputFile, outputFile):
                    tool.run(inputFile, outputFile, flags + optimization.flags)
                    if os.path.exists(outputFile):
                        yield outputFile
                        for optimizer in optimizers:
                            base, ext = os.path.splitext(outputFile)
                            opt_outputFile = base + '_' + optimizer.name + ext
                            optimizer.run(outputFile, opt_outputFile, [])
                            if os.path.exists(opt_outputFile):
                                yield opt_outputFile

def multicompileFiles(inputFiles, outputDir, tools, flags, optimizations, target, optimizers=None):
    """Produces ll files for all given input files using the provided tool, and applies all optimizations specified by the optimizer tool"""
    for f in inputFiles:
        yield f, list(multicompileFile(f, outputDir, tools, flags, optimizations, target, optimizers=optimizers))

def multicompileFolder(path, outputDir, tools, flags, optimizations, target, optimizers=None, excludes=None):
    """Produces ll files for all files in given directory using the provided tool, and applies all optimizations specified by the optimizer tool"""
    for f in findRecursively(path, excludes):
        yield f, list(multicompileFile(f, outputDir, tools, flags, optimizations, target, optimizers=optimizers))

def multicompileRefFile(inputFile, outputDir, tools, flags):
    lang = ProgrammingLanguage.lookupFile(inputFile)
    for tool in tools:
        if tool.supports(lang):
            referenceFile = getReferenceName(inputFile, outputDir, ProgrammingLanguage.EXEC)
            if not isFileUpToDate(inputFile, referenceFile):
                tool.compileReferenceFile(inputFile, referenceFile, flags)
                if os.path.exists(referenceFile):
                    yield referenceFile

def multicompileRefFolder(path, outputDir, tools, flags, excludes=None):
    """Produces executables for all files in given directory using the provided tool"""
    for f in findRecursively(path, excludes):
        yield f, list(multicompileRefFile(f, outputDir, tools, flags))

def printProgress(iterator):
    for x in iterator:
        if len(x) < 2 or len(x[1]) > 0:
            print('.', end='')
    print(' done')
