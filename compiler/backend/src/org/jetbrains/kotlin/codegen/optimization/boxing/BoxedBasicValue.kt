/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.codegen.optimization.boxing

import com.intellij.openapi.util.Pair
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.optimization.common.StrictBasicValue
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import java.util.*

abstract class BoxedBasicValue(type: Type) : StrictBasicValue(type) {
    abstract val descriptor: BoxedValueDescriptor
    abstract fun taint(): BoxedBasicValue

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}


class CleanBoxedValue(
    boxedType: Type,
    boxingInsn: AbstractInsnNode,
    progressionIterator: ProgressionIteratorBasicValue?
) : BoxedBasicValue(boxedType) {
    override val descriptor: BoxedValueDescriptor = BoxedValueDescriptor(boxedType, boxingInsn, progressionIterator)

    private var tainted: TaintedBoxedValue? = null
    override fun taint(): BoxedBasicValue = tainted ?: TaintedBoxedValue(this).also { tainted = it }
}


class TaintedBoxedValue(private val boxedBasicValue: CleanBoxedValue) : BoxedBasicValue(boxedBasicValue.type) {
    override val descriptor: BoxedValueDescriptor get() = boxedBasicValue.descriptor

    override fun taint(): BoxedBasicValue = this
}


class BoxedValueDescriptor(
    private val boxedType: Type,
    val boxingInsn: AbstractInsnNode,
    val progressionIterator: ProgressionIteratorBasicValue?
) {
    private val associatedInsns = HashSet<AbstractInsnNode>()
    private val unboxingWithCastInsns = HashSet<Pair<AbstractInsnNode, Type>>()
    private val associatedVariables = HashSet<Int>()
    private val mergedWith = HashSet<BoxedValueDescriptor>()

    var isSafeToRemove: Boolean = true; private set
    val unboxedType: Type = getUnboxedType(boxedType)

    fun getAssociatedInsns(): List<AbstractInsnNode> = associatedInsns.toList()

    fun addInsn(insnNode: AbstractInsnNode) {
        associatedInsns.add(insnNode)
    }

    fun addVariableIndex(index: Int) {
        associatedVariables.add(index)
    }

    fun getVariablesIndexes(): List<Int> =
        ArrayList(associatedVariables)

    fun addMergedWith(descriptor: BoxedValueDescriptor) {
        mergedWith.add(descriptor)
    }

    fun getMergedWith(): Iterable<BoxedValueDescriptor> =
        mergedWith

    fun markAsUnsafeToRemove() {
        isSafeToRemove = false
    }

    fun isDoubleSize(): Boolean = unboxedType.size == 2

    fun isFromProgressionIterator(): Boolean = progressionIterator != null

    fun addUnboxingWithCastTo(insn: AbstractInsnNode, type: Type) {
        unboxingWithCastInsns.add(Pair.create(insn, type))
    }

    fun getUnboxingWithCastInsns(): Set<Pair<AbstractInsnNode, Type>> =
        unboxingWithCastInsns
}


fun getUnboxedType(boxedType: Type): Type {
    val primitiveType = AsmUtil.unboxPrimitiveTypeOrNull(boxedType)
    if (primitiveType != null) return primitiveType

    if (boxedType == AsmTypes.K_CLASS_TYPE) return AsmTypes.JAVA_CLASS_TYPE

    throw IllegalArgumentException("Expected primitive type wrapper or KClass, got: $boxedType")
}
