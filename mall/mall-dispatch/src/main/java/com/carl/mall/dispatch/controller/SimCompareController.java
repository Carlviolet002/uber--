package com.carl.mall.dispatch.controller;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端策略对比：读取 mall-sim 离线仿真产物 sim-result.csv，返回 A/B/C 三策略指标对比。
 * mall-sim 保持非 Web（CommandLineRunner 写 CSV），本接口仅只读解析，零侵入。
 */
@RestController
@RequestMapping("/dispatch/admin")
public class SimCompareController {

    /** 仿真 CSV 路径（可配，默认相对工作目录）；为空时按候选路径依次探测 */
    @Value("${mall.sim.csv:}")
    private String configuredCsv;

    private void checkAdmin(long roles) {
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN)) {
            throw new BizException(403, "无权限");
        }
    }

    @GetMapping("/sim-compare")
    public Result<Map<String, Object>> simCompare(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        File file = resolveCsv();
        Map<String, Object> result = new LinkedHashMap<>();
        if (file == null || !file.exists()) {
            result.put("generatedAt", null);
            result.put("rows", Collections.emptyList());
            result.put("empty", true);
            result.put("message", "尚未生成仿真数据，请先运行 mall-sim");
            return Result.ok(result);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null) {
                result.put("rows", rows);
                result.put("empty", true);
                return Result.ok(result);
            }
            String[] cols = header.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] vals = line.split(",");
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < cols.length && i < vals.length; i++) {
                    String c = cols[i].trim();
                    String v = vals[i].trim();
                    if (isNumeric(v)) {
                        row.put(c, Double.parseDouble(v));
                    } else {
                        row.put(c, v);
                    }
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new BizException("读取仿真数据失败：" + e.getMessage());
        }
        result.put("generatedAt", new java.util.Date(file.lastModified()).toString());
        result.put("rows", rows);
        result.put("empty", rows.isEmpty());
        return Result.ok(result);
    }

    /** 解析 CSV 路径：优先配置项，否则依次探测多个候选目录（mall-sim 与 dispatch 工作目录可能不同），并向上回溯父目录 */
    private File resolveCsv() {
        if (configuredCsv != null && !configuredCsv.isBlank()) {
            return new File(configuredCsv);
        }
        String[] candidates = {
                "sim-output/sim-result.csv",
                "./sim-output/sim-result.csv",
                "../sim-output/sim-result.csv",
                "../../sim-output/sim-result.csv",
                "../mall-sim/sim-output/sim-result.csv",
                "./mall-sim/sim-output/sim-result.csv",
                "mall-sim/sim-output/sim-result.csv"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) {
                return f;
            }
        }
        // 向上回溯至多 5 级父目录，寻找 sim-output/sim-result.csv（适配不同启动目录）
        File dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 5 && dir != null; i++) {
            File candidate = new File(dir, "sim-output/sim-result.csv");
            if (candidate.exists()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        return new File(candidates[0]);
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
