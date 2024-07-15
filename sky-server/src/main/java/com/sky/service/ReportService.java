package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {

    /**
     * 统计指定时间区间的营业额统计
     * @param begin
     * @param end
     * @return
     */
    TurnoverReportVO turnover(LocalDate begin, LocalDate end);

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    UserReportVO user(LocalDate begin, LocalDate end);
}
