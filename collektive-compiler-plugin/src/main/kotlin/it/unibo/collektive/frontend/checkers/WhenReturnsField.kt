/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.frontend.checkers

import it.unibo.collektive.frontend.checkers.CheckersUtility.isInsideAggregateFunction
import it.unibo.collektive.utils.common.AggregateFunctionNames.FIELD_CLASS
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirWhenExpressionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type

/**
 * A FIR (Front-end IR) expression checker that verifies whether `when` expressions
 * inside aggregate functions return values of type [it.unibo.collektive.aggregate.Field].
 *
 * ### Purpose
 * This checker ensures **semantic alignment safety** in aggregate programs by disallowing
 * the return of `Field` objects from conditional branches such as `when` expressions.
 * Returning `Field` instances from different branches can cause **alignment issues**
 * that violate the aggregate computing model enforced by Collektive.
 *
 * ### Applicability
 * This checker only triggers when:
 * - The `when` expression appears inside an aggregate function
 * (as identified by [CheckersUtility.isInsideAggregateFunction]).
 * - The current function is *not* `it.unibo.collektive.aggregate.api.impl.project`.
 * - The `when` expression's resolved type corresponds to [it.unibo.collektive.aggregate.Field].
 *
 * ### Example Violation
 * ```kotlin
 * val result = when {
 *     cond1 -> neighboring(1)
 *     else -> neighboring(2)
 * }
 * ```
 * This will raise an error because both branches return `Field` objects.
 *
 * ### Suggested Fix
 * Instead of returning `Field`s directly from branches, refactor to use `alignedMapValues`:
 * ```kotlin
 * neighboring(1).alignedMapValues(neighboring(2)) { _, y, z -> if (cond1) y else z }
 * ```
 *
 * @see org.jetbrains.kotlin.fir.analysis.checkers.expression.FirWhenExpressionChecker
 * @see it.unibo.collektive.utils.common.AggregateFunctionNames.FIELD_CLASS
 */
object WhenReturnsField : FirWhenExpressionChecker(MppCheckerKind.Common) {
    override fun check(expression: FirWhenExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        if (expression.returnsAField() && context.isInsideAggregateFunction() && !context.isAggregateProject()) {
            reporter.reportOn(
                expression.source,
                FirCollektiveErrors.BRANCH_RETURNS_FIELD,
                FIELD_CLASS + expression.resolvedType.typeArguments.joinToString(
                    prefix = "<",
                    postfix = ">",
                    separator = ", ",
                ) { (it.type?.classId?.shortClassName ?: it.type).toString() },
                context,
            )
        }
    }

    private fun FirWhenExpression.returnsAField() = FIELD_CLASS == returnType()

    private fun FirWhenExpression.returnType() = resolvedType.classId?.asFqNameString()

    private fun FirTypeRef.isField() = FIELD_CLASS == coneType.classId?.asFqNameString()

    private fun FirValueParameter.isField() = returnTypeRef.isField()

    private fun CheckerContext.isAggregateProject() = containingElements
        .firstNotNullOfOrNull { it as? FirSimpleFunction }
        ?.run {
            symbol.callableId.asSingleFqName().toString() == "it.unibo.collektive.aggregate.api.impl.project" &&
                this.valueParameters.size == 1 &&
                valueParameters.first().isField() &&
                returnTypeRef.isField()
        } == true
}
