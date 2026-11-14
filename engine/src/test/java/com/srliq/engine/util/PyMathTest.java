package com.srliq.engine.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Valeurs vérifiées avec CPython 3.13 / numpy 2.5. */
class PyMathTest {

    @Test
    void pythonRoundIsHalfEvenOnExactBinaryValue() {
        assertEquals(2.67, PyMath.pyRound(2.675, 2)); // 2.67499999...
        assertEquals(0.12, PyMath.pyRound(0.125, 2)); // demi exact -> pair
        assertEquals(1.0, PyMath.pyRound(1.005, 2));
        assertEquals(4.04, PyMath.pyRound(4.0449999, 2));
    }

    @Test
    void numpyRoundMultipliesRintsDivides() {
        assertEquals(2.68, PyMath.npRound(2.675, 2)); // 267.49999999999997 -> 267.5 -> 268
        assertEquals(0.28, PyMath.npRound(0.285, 2));
        assertEquals(0.12, PyMath.npRound(0.125, 2));
        assertEquals(240.0, PyMath.npRound(239.5, 0));
    }

    @Test
    void pythonFixedFormat() {
        assertEquals("1.00", PyMath.format2(1.005));
        assertEquals("0.12", PyMath.format2(0.125));
        assertEquals("1995.41", PyMath.format2(1995.41));
        assertEquals("-0.00", PyMath.format2(-0.001));
    }

    @Test
    void searchSortedSemantics() {
        long[] a = {1, 3, 3, 5};
        assertEquals(3, PyMath.searchRight(a, 3));
        assertEquals(1, PyMath.searchLeft(a, 3));
        assertEquals(0, PyMath.searchRight(a, 0));
        assertEquals(4, PyMath.searchLeft(a, 6));
    }

    @Test
    void pairwiseSumMatchesNumpyOrder() {
        double[] a = new double[9];
        for (int i = 0; i < a.length; i++) {
            a[i] = 0.1 * (i + 1);
        }
        // np.array([0.1*(i+1) for i in range(9)]).sum() == 4.5 (somme séquentielle : 4.500000000000001)
        assertEquals(4.5, PyMath.pairwiseSum(a));
    }
}
