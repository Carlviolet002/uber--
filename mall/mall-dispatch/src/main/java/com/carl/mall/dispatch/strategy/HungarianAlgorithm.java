package com.carl.mall.dispatch.strategy;

import java.util.Arrays;

/**
 * 匈牙利算法（Kuhn–Munkres）最小权指派，O(n³)。
 *
 * <p>本类为 e-maxx 经典实现（Hungarian Algorithm for minimum-cost perfect matching），
 * 用于策略 C 的"订单 × 骑手"批次全局最优指派。</p>
 *
 * <p>论文素材要点：</p>
 * <ul>
 *   <li>时间复杂度 O(n³)（n 为矩阵行数，n &lt;= m 列数），远优于暴力枚举的 O(n!)；</li>
 *   <li>算法基于"可行顶标 u/v + 相等子图增广"的原始-对偶思想；</li>
 *   <li>当订单数 n 大于骑手数 m 时，调用方补虚拟骑手列（代价 INF 占位），使矩阵退化为 n×n；
 *       存在 INF 时算法仍正确工作，只要 INF 足够大且有效配对存在；</li>
 *   <li>代价为 INF 的最终配对由调用方视为"未指派"。</li>
 * </ul>
 */
public final class HungarianAlgorithm {

    private HungarianAlgorithm() {
    }

    /**
     * 最小权指派。cost 为 n×m 矩阵（n&lt;=m），元素不可为 NaN；不合法配对用大数 INF 占位。
     *
     * @return p 数组长度 m+1；p[j] 表示第 j 列（1-based）被第 p[j] 行（1-based）指派，
     *         p[j]=0 表示该列未指派
     */
    public static int[] solve(double[][] cost) {
        int n = cost.length;
        int m = cost[0].length;
        if (n > m) {
            throw new IllegalArgumentException("匈牙利算法要求行数 n <= 列数 m（请先补虚拟骑手）");
        }

        // u[i]：第 i 行的顶标（对偶变量）；v[j]：第 j 列的顶标
        double[] u = new double[n + 1];
        double[] v = new double[m + 1];
        // p[j]：当前增广森林中第 j 列匹配到的行（1-based）；p[0] 存放本轮正在增广的行
        int[] p = new int[m + 1];
        // way[j]：松弛路径上第 j 列的前驱列，用于回退重建增广路
        int[] way = new int[m + 1];

        // 逐行加入匹配，每次为一行找到最小增量增广路
        for (int i = 1; i <= n; i++) {
            p[0] = i;                       // 虚拟 0 列连到当前行 i，作为增广路起点
            int j0 = 0;                     // 当前访问的列
            double[] minv = new double[m + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY); // 各列当前最小松弛值
            boolean[] used = new boolean[m + 1];          // 列是否已访问（在交替树上）

            // 迭代增广：直到找到一条能匹配新行的增广路
            do {
                used[j0] = true;
                int i0 = p[j0];              // 当前列 j0 匹配的行（起点时是虚拟 0 列连的当前行 i）
                int j1 = -1;                 // 本轮松弛值最小的未访问列
                double delta = Double.POSITIVE_INFINITY;

                // 扫描所有未访问列，维护 minv 并找出全局最小增量 delta
                for (int j = 1; j <= m; j++) {
                    if (!used[j]) {
                        // 松弛值 = 行 i0 到列 j 的代价 - 行顶标 u - 列顶标 v
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;      // 记录松弛来源列（增广路前驱）
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }

                // 更新可行顶标：已访问列所在行 u 增加 delta，已访问列 v 减少 delta，
                // 未访问列 minv 减去 delta（保持相等子图性质）
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);            // 直到走到一个未匹配列，增广路完成

            // 沿 way 回退，翻转增广路上的匹配边
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        return p;
    }
}
