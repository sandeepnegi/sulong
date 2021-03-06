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
package com.oracle.truffle.llvm.parser.bc.impl.nodes;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNodeGen;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMAllocInstructionFactory;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMStoreNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMBitcodeFunctionVisitor;
import com.oracle.truffle.llvm.parser.factories.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMCastsFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMComparisonFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMFrameReadWriteFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMGetElementPtrFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLiteralFactory;
import com.oracle.truffle.llvm.parser.factories.LLVMLogicalFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionType;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;

public final class LLVMNodeGenerator {

    private final LLVMBitcodeFunctionVisitor method;

    private final LLVMParserRuntime runtime;

    public LLVMNodeGenerator(LLVMBitcodeFunctionVisitor method) {
        this.method = method;
        this.runtime = method.getModule().getParserRuntime();
    }

    public static Integer evaluateIntegerConstant(Symbol constant) {
        if (constant instanceof IntegerConstant) {
            if (constant.getType() == IntegerType.LONG) {
                LLVMLogger.info("GEP index overflow (still parse as int");
            }
            return (int) ((IntegerConstant) constant).getValue();

        } else if (constant instanceof BigIntegerConstant) {
            LLVMLogger.info("GEP index overflow (still parse as int");
            return ((BigIntegerConstant) constant).getValue().intValueExact();

        } else if (constant instanceof NullConstant) {
            return 0;

        } else {
            return null;
        }
    }

    public static LLVMExpressionNode generateBinaryOperatorNode(BinaryOperator operator, LLVMBaseType type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        final LLVMArithmeticInstructionType arithmeticOperationType = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operator);
        if (arithmeticOperationType != null) {
            return LLVMArithmeticFactory.createArithmeticOperation(lhs, rhs, arithmeticOperationType, type, null);
        }

        final LLVMLogicalInstructionType logicalOperationType = LLVMBitcodeTypeHelper.toLogicalInstructionType(operator);
        if (logicalOperationType != null) {
            return LLVMLogicalFactory.createLogicalOperation(lhs, rhs, logicalOperationType, type, null);
        }

        throw new RuntimeException("Missed a binary operator");
    }

    private static LLVMExpressionNode resolveBigIntegerConstant(BigIntegerConstant constant) {
        final int bits = ((IntegerType) constant.getType()).getBits();
        return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.create(bits, constant.getValue().toByteArray()));
    }

    private static LLVMExpressionNode resolveIntegerConstant(IntegerConstant constant) {
        final int bits = ((IntegerType) constant.getType()).getBits();
        switch (bits) {
            case 1:
                return new LLVMSimpleLiteralNode.LLVMI1LiteralNode(constant.getValue() != 0);
            case Byte.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI8LiteralNode((byte) constant.getValue());
            case Short.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI16LiteralNode((short) constant.getValue());
            case Integer.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI32LiteralNode((int) constant.getValue());
            case Long.SIZE:
                return new LLVMSimpleLiteralNode.LLVMI64LiteralNode(constant.getValue());
            default:
                return new LLVMSimpleLiteralNode.LLVMIVarBitLiteralNode(LLVMIVarBit.fromLong(bits, constant.getValue()));
        }
    }

    private static LLVMExpressionNode resolveMetadataConstant(MetadataConstant constant) {
        // TODO: point to Metadata
        return new LLVMSimpleLiteralNode.LLVMI64LiteralNode(constant.getValue());
    }

    public LLVMExpressionNode resolveElementPointer(Symbol base, List<Symbol> indices) {
        LLVMExpressionNode currentAddress = resolve(base);
        Type currentType = base.getType();

        for (final Symbol symbol : indices) {
            final Type type = symbol.getType();

            final Integer constantIndex = LLVMNodeGenerator.evaluateIntegerConstant(symbol);
            if (constantIndex == null) {
                final int indexedTypeLength = runtime.getIndexOffset(1, currentType);
                currentType = currentType.getIndexType(1);
                final LLVMExpressionNode valueref = resolve(symbol);
                currentAddress = LLVMGetElementPtrFactory.create(type.getLLVMBaseType(), (LLVMAddressNode) currentAddress, valueref, indexedTypeLength);

            } else {
                final int indexedTypeLength = runtime.getIndexOffset(constantIndex, currentType);
                currentType = currentType.getIndexType(constantIndex);
                if (indexedTypeLength != 0) {
                    final LLVMExpressionNode constantNode;
                    switch (type.getLLVMBaseType()) {
                        case I32:
                            constantNode = new LLVMSimpleLiteralNode.LLVMI32LiteralNode(1);
                            break;
                        case I64:
                            constantNode = new LLVMSimpleLiteralNode.LLVMI64LiteralNode(1L);
                            break;
                        default:
                            throw new AssertionError();
                    }
                    currentAddress = LLVMGetElementPtrFactory.create(type.getLLVMBaseType(), (LLVMAddressNode) currentAddress, constantNode, indexedTypeLength);
                }
            }
        }

        return currentAddress;
    }

    public LLVMExpressionNode resolve(Symbol symbol) {
        if (symbol instanceof ValueInstruction || symbol instanceof FunctionParameter) {
            final FrameSlot slot = method.getFrame().findFrameSlot(((ValueSymbol) symbol).getName());
            return LLVMFrameReadWriteFactory.createFrameRead(symbol.getType().getLLVMBaseType(), slot);

        } else if (symbol instanceof GlobalValueSymbol) {
            return method.global((GlobalValueSymbol) symbol);

        } else if (symbol instanceof FunctionDefinition || symbol instanceof FunctionDeclaration) {
            return resolveFunction(((ValueSymbol) symbol).getName(), (FunctionType) symbol);

        } else if (symbol instanceof BinaryOperationConstant) {
            return resolveBinaryOperationConstant((BinaryOperationConstant) symbol);

        } else if (symbol instanceof BlockAddressConstant) {
            return resolveBlockAddressConstant((BlockAddressConstant) symbol);

        } else if (symbol instanceof CastConstant) {
            return resolveCastConstant((CastConstant) symbol);

        } else if (symbol instanceof CompareConstant) {
            return resolveCompareConstant((CompareConstant) symbol);

        } else if (symbol instanceof GetElementPointerConstant) {
            return resolveGetElementPointerConstant((GetElementPointerConstant) symbol);

        } else if (symbol instanceof IntegerConstant) {
            return resolveIntegerConstant((IntegerConstant) symbol);

        } else if (symbol instanceof BigIntegerConstant) {
            return resolveBigIntegerConstant((BigIntegerConstant) symbol);

        } else if (symbol instanceof FloatingPointConstant) {
            return LLVMConstantGenerator.toFloatingPointConstant((FloatingPointConstant) symbol);

        } else if (symbol instanceof NullConstant || symbol instanceof UndefinedConstant) {
            return LLVMConstantGenerator.toConstantZeroNode(symbol.getType(), method.getContext(), method.getStackSlot(), runtime);

        } else if (symbol instanceof StructureConstant) {
            return resolveStructureConstant((StructureConstant) symbol);

        } else if (symbol instanceof ArrayConstant) {
            return resolveArrayConstant((ArrayConstant) symbol);

        } else if (symbol instanceof VectorConstant) {
            return resolveVectorConstant((VectorConstant) symbol);

        } else if (symbol instanceof MetadataConstant) {
            return resolveMetadataConstant((MetadataConstant) symbol);

        } else {
            throw new AssertionError("Cannot resolve symbol: " + symbol);
        }
    }

    private LLVMExpressionNode resolveArrayConstant(ArrayConstant constant) {

        final int baseTypeSize = runtime.getByteSize(constant.getType().getElementType());
        final LLVMAddressNode arrayAlloc = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(constant.getElementCount() * baseTypeSize,
                        runtime.getByteAlignment(constant.getType()),
                        method.getContext(), method.getStackSlot());

        final List<LLVMExpressionNode> arrayValues = new ArrayList<>(constant.getElementCount());
        for (int i = 0; i < constant.getElementCount(); i++) {
            arrayValues.add(resolve(constant.getElement(i)));
        }

        switch (constant.getType().getElementType().getLLVMBaseType()) {
            case I8:
                return LLVMStoreNodeFactory.LLVMI8ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI8Node[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case I16:
                return LLVMStoreNodeFactory.LLVMI16ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI16Node[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case I32:
                return LLVMStoreNodeFactory.LLVMI32ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI32Node[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case I64:
                return LLVMStoreNodeFactory.LLVMI64ArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMI64Node[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case FLOAT:
                return LLVMStoreNodeFactory.LLVMFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMFloatNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case DOUBLE:
                return LLVMStoreNodeFactory.LLVMDoubleArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMDoubleNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case X86_FP80:
                return LLVMStoreNodeFactory.LLVM80BitFloatArrayLiteralNodeGen.create(arrayValues.toArray(new LLVM80BitFloatNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case ARRAY:
            case STRUCT:
                return LLVMStoreNodeFactory.LLVMAddressArrayCopyNodeGen.create(arrayValues.toArray(new LLVMAddressNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case ADDRESS:
                return LLVMStoreNodeFactory.LLVMAddressArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMAddressNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            case FUNCTION_ADDRESS:
                return LLVMStoreNodeFactory.LLVMFunctionArrayLiteralNodeGen.create(arrayValues.toArray(new LLVMFunctionNode[constant.getElementCount()]), baseTypeSize, arrayAlloc);
            default:
                throw new AssertionError("Cannot create array literal for element type: " + constant.getType().getElementType());
        }
    }

    private LLVMExpressionNode resolveStructureConstant(StructureConstant constant) {
        final int structSize = runtime.getByteSize(constant.getType());
        final int structAlignment = runtime.getByteAlignment(constant.getType());
        final LLVMExpressionNode alloc = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(structSize, structAlignment, method.getContext(), method.getStackSlot());

        final int[] offsets = new int[constant.getElementCount()];
        final LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[constant.getElementCount()];
        int currentOffset = 0;
        for (int i = 0; i < constant.getElementCount(); i++) {
            final Type elemType = constant.getElementType(i);

            if (!constant.isPacked()) {
                currentOffset += runtime.getBytePadding(currentOffset, elemType);
            }

            offsets[i] = currentOffset;
            nodes[i] = createStructWriteNode(resolve(constant.getElement(i)), elemType);
            currentOffset += runtime.getByteSize(elemType);
        }

        return new StructLiteralNode(offsets, nodes, (LLVMAddressNode) alloc);
    }

    private LLVMStructWriteNode createStructWriteNode(LLVMExpressionNode parsedConstant, Type type) {
        final LLVMBaseType llvmType = type.getLLVMBaseType();
        switch (llvmType) {
            case I1:
                return new StructLiteralNode.LLVMI1StructWriteNode((LLVMI1Node) parsedConstant);
            case I8:
                return new StructLiteralNode.LLVMI8StructWriteNode((LLVMI8Node) parsedConstant);
            case I16:
                return new StructLiteralNode.LLVMI16StructWriteNode((LLVMI16Node) parsedConstant);
            case I32:
                return new StructLiteralNode.LLVMI32StructWriteNode((LLVMI32Node) parsedConstant);
            case I64:
                return new StructLiteralNode.LLVMI64StructWriteNode((LLVMI64Node) parsedConstant);
            case FLOAT:
                return new StructLiteralNode.LLVMFloatStructWriteNode((LLVMFloatNode) parsedConstant);
            case DOUBLE:
                return new StructLiteralNode.LLVMDoubleStructWriteNode((LLVMDoubleNode) parsedConstant);
            case X86_FP80:
                return new StructLiteralNode.LLVM80BitFloatStructWriteNode((LLVM80BitFloatNode) parsedConstant);
            case ARRAY:
            case STRUCT:
                final int byteSize = runtime.getByteSize(type);
                if (byteSize == 0) {
                    return new StructLiteralNode.LLVMEmptyStructWriteNode();
                } else {
                    return new StructLiteralNode.LLVMCompoundStructWriteNode((LLVMAddressNode) parsedConstant, byteSize);
                }
            case ADDRESS:
                return new StructLiteralNode.LLVMAddressStructWriteNode((LLVMAddressNode) parsedConstant);
            case FUNCTION_ADDRESS:
                return new StructLiteralNode.LLVMFunctionStructWriteNode((LLVMFunctionNode) parsedConstant);
            default:
                throw new AssertionError(llvmType);
        }
    }

    private LLVMExpressionNode resolveBinaryOperationConstant(BinaryOperationConstant constant) {
        final LLVMExpressionNode lhs = resolve(constant.getLHS());
        final LLVMExpressionNode rhs = resolve(constant.getRHS());
        final LLVMBaseType type = constant.getType().getLLVMBaseType();
        return generateBinaryOperatorNode(constant.getOperator(), type, lhs, rhs);
    }

    private LLVMExpressionNode resolveBlockAddressConstant(BlockAddressConstant constant) {
        final int val = method.labels().get(constant.getInstructionBlock().getName());
        return new LLVMSimpleLiteralNode.LLVMAddressLiteralNode(LLVMAddress.fromLong(val));
    }

    private LLVMExpressionNode resolveCastConstant(CastConstant constant) {
        final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(constant.getOperator());
        final LLVMExpressionNode fromNode = resolve(constant.getValue());
        final LLVMBaseType from = constant.getValue().getType().getLLVMBaseType();
        final LLVMBaseType to = constant.getType().getLLVMBaseType();
        return LLVMCastsFactory.cast(fromNode, to, from, type);
    }

    private LLVMExpressionNode resolveCompareConstant(CompareConstant constant) {
        final LLVMExpressionNode lhs = resolve(constant.getLHS());
        final LLVMExpressionNode rhs = resolve(constant.getRHS());
        return LLVMComparisonFactory.toCompareNode(constant.getOperator(), constant.getLHS().getType(), lhs, rhs);
    }

    private LLVMExpressionNode resolveFunction(String name, FunctionType type) {
        final LLVMFunctionDescriptor.LLVMRuntimeType returnType = type.getReturnType().getRuntimeType();
        final LLVMFunctionDescriptor.LLVMRuntimeType[] argTypes = LLVMBitcodeTypeHelper.toRuntimeTypes(type.getArgumentTypes());
        return LLVMFunctionLiteralNodeGen.create((LLVMFunctionDescriptor) method.getContext().getFunctionRegistry().createFunctionDescriptor(name, returnType, argTypes, type.isVarArg()));
    }

    private LLVMExpressionNode resolveGetElementPointerConstant(GetElementPointerConstant constant) {
        final LLVMExpressionNode baseAddress = resolve(constant.getBasePointer());
        final List<Symbol> indices = constant.getIndices();

        LLVMExpressionNode currentAddress = baseAddress;
        Type currentType = constant.getBasePointer().getType();
        Type parentType = null;
        int currentOffset = 0;

        for (final Symbol index : indices) {
            final Integer indexVal = evaluateIntegerConstant(index);
            if (indexVal == null) {
                throw new IllegalStateException("Invalid index: " + index);
            }

            currentOffset += runtime.getIndexOffset(indexVal, currentType);
            parentType = currentType;
            currentType = currentType.getIndexType(indexVal);
        }

        if (currentType != null && !((parentType instanceof StructureType) && (((StructureType) parentType).isPacked()))) {
            currentOffset += runtime.getBytePadding(currentOffset, currentType);
        }

        if (currentOffset != 0) {
            currentAddress = LLVMGetElementPtrFactory.create(LLVMBaseType.I32, (LLVMAddressNode) currentAddress, new LLVMSimpleLiteralNode.LLVMI32LiteralNode(1), currentOffset);
        }

        return currentAddress;
    }

    private LLVMExpressionNode resolveVectorConstant(VectorConstant constant) {
        final List<LLVMExpressionNode> values = new ArrayList<>();
        for (int i = 0; i < constant.getLength(); i++) {
            values.add(resolve(constant.getElement(i)));
        }

        final LLVMAddressNode target = LLVMAllocInstructionFactory.LLVMAllocaInstructionNodeGen.create(runtime.getByteSize(constant.getType()),
                        runtime.getByteAlignment(constant.getType()),
                        method.getContext(),
                        method.getStackSlot());

        return LLVMLiteralFactory.createVectorLiteralNode(values, target, constant.getType().getLLVMBaseType());
    }
}
