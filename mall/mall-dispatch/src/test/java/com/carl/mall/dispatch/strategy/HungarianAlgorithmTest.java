package com.carl.mall.dispatch.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 匈牙利算法求解器（Kuhn–Munkres）单元测试。
 * solve 返回 p 数组长度 m+1；p[j] 表示第 j 列（1-based）被第 p[j] 行（1-based）指派，p[j]=0 表示未指派。
 */
class HungarianAlgorithmTest {

    @Test
    void solvesTwoByTwoDiagonal() {
        double[][] cost = {{1, 2}, {2, 1}};
        int[] p = HungarianAlgorithm.solve(cost);
        assertEquals(1, p[1]); // 列0 -> 行0
        assertEquals(2, p[2]); // 列1 -> 行1
    }

    @Test
    void solvesCrossAssignment() {
        // 最优：row0->col1, row1->col2, row2->col0，总代价 3
        double[][] cost = {{9, 1, 9}, {9, 9, 1}, {1, 9, 9}};
        int[] p = HungarianAlgorithm.solve(cost);
        assertEquals(3, p[1]); // 列0 -> 行2
        assertEquals(1, p[2]); // 列1 -> 行0
        assertEquals(2, p[3]); // 列2 -> 行1
    }

    @Test
    void solvesThreeByThreeDiagonal() {
        double[][] cost = {{1, 9, 9}, {9, 1, 9}, {9, 9, 1}};
        int[] p = HungarianAlgorithm.solve(cost);
        assertEquals(1, p[1]);
        assertEquals(2, p[2]);
        assertEquals(3, p[3]);
    }

    @Test
    void throwsWhenRowsExceedCols() {
        double[][] cost = {{1, 2}, {3, 4}, {5, 6}}; // 3x2
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(cost));
    }
}
