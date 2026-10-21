package com.arturpelicho.calcexpress.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MatrixTest {
    private fun mat(rows: Int, cols: Int, vararg v: String) =
        MatrixNode(rows, cols, v.mapTo(mutableListOf()) { t -> RowBuilder.build { if (t.startsWith("-")) { minus(); num(t.drop(1)) } else num(t) } })

    private fun eval(block: RowBuilder.() -> Unit) = Analysis.evaluate(listOf(RowBuilder.build(block)), AngleUnit.RAD)

    private fun near(e: Double, a: Double) = assertTrue(abs(e - a) < 1e-9, "expected $e got $a")

    @Test fun determinant() {
        val o = eval { fn(Fn.DET) { row.items += mat(2, 2, "1", "2", "3", "4") } }
        near(-2.0, (o as Outcome.Value).value)
        val o3 = eval { fn(Fn.DET) { row.items += mat(3, 3, "2", "0", "1", "1", "3", "2", "1", "1", "2") } }
        near(6.0, (o3 as Outcome.Value).value)
    }

    @Test fun productAndSum() {
        val o = eval { row.items += mat(2, 2, "1", "2", "3", "4"); times(); row.items += mat(2, 1, "5", "6") }
        val m = (o as Outcome.MatrixValue).matrix
        assertEquals(2, m.rows); assertEquals(1, m.cols)
        near(17.0, m[0, 0]); near(39.0, m[1, 0])
        val s = eval { num("2"); row.items += mat(1, 2, "1", "2"); plus(); row.items += mat(1, 2, "3", "4") }
        val sm = (s as Outcome.MatrixValue).matrix
        near(5.0, sm[0, 0]); near(8.0, sm[0, 1])
    }

    @Test fun inverseViaPowerAndLinearSystem() {
        // x + y = 3, x − y = 1 → (2, 1)
        val o = eval { row.items += mat(2, 2, "1", "1", "1", "-1"); pow { minus(); num("1") }; times(); row.items += mat(2, 1, "3", "1") }
        val m = (o as Outcome.MatrixValue).matrix
        near(2.0, m[0, 0]); near(1.0, m[1, 0])
    }

    @Test fun singularAndDimensionErrors() {
        val s = eval { row.items += mat(2, 2, "1", "2", "2", "4"); pow { minus(); num("1") } }
        assertEquals(CalcException.Reason.MATRIX_SINGULAR, (s as Outcome.Error).reason)
        val d = eval { row.items += mat(2, 2, "1", "2", "3", "4"); plus(); row.items += mat(1, 2, "1", "2") }
        assertEquals(CalcException.Reason.MATRIX_DIMENSIONS, (d as Outcome.Error).reason)
    }

    @Test fun transposeAndTrace() {
        val t = eval { fn(Fn.TRANSPOSE) { row.items += mat(2, 3, "1", "2", "3", "4", "5", "6") } }
        val m = (t as Outcome.MatrixValue).matrix
        assertEquals(3, m.rows); near(4.0, m[0, 1]); near(3.0, m[2, 0])
        val tr = eval { fn(Fn.TRACE) { row.items += mat(2, 2, "1", "2", "3", "4") } }
        near(5.0, (tr as Outcome.Value).value)
    }

    @Test fun eigenvaluesThroughEquation() {
        // det([[2,1],[1,2]] − λI) = 0 → λ = 1, 3
        val o = eval {
            fn(Fn.DET) { row.items += mat(2, 2, "2", "1", "1", "2"); minus(); v("λ"); times(); row.items += mat(2, 2, "1", "0", "0", "1") }
            eq(); num("0")
        }
        val roots = (o as Outcome.Solutions).values.single().second
        assertEquals(2, roots.size); near(1.0, roots[0]); near(3.0, roots[1])
    }

    @Test fun editorAddsRowsAndColumns() {
        val e = Editor(); e.matrix(2, 2)
        assertTrue(e.inMatrix)
        e.digit('1'); e.addMatrixColumn(); e.digit('2')
        val m = e.lines[0].items[0] as MatrixNode
        assertEquals(3, m.colCount); assertEquals("[1,2,;,,]", m.toString())
        e.addMatrixRow(); assertEquals(3, m.rowCount)
        e.removeMatrixRow(); assertEquals(2, m.rowCount)
        e.removeMatrixColumn(); assertEquals(2, m.colCount)
    }

    @Test fun elasticityFormulas() {
        val g = Formulas.all.first { it.id == "shear_modulus" }
        val e = Editor(); e.load(g.build(UnitSystem.METRIC))
        // G empty (unknown); fill E = 200e9 → use 200, ν = 0.3
        e.nextEmptyCase(); e.digit('2'); e.digit('0'); e.digit('0'); e.nextEmptyCase(); e.point(); e.digit('3')
        val o = Analysis.evaluate(e.lines, AngleUnit.RAD) as Outcome.Solutions
        near(200 / 2.6, o.values.single().second.single())
    }
}
