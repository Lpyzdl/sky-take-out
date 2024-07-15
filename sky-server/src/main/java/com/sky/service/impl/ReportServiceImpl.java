package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    /**
     * 统计指定时间区间的营业额统计(已经完成的订单总金额)
     *
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO turnover(LocalDate begin, LocalDate end) {
        //用于存放begin到end范围内每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        while (begin.isBefore(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        dateList.add(end);

        //查询dateList中每个日期的营业额
        ArrayList<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询date日期对应的营业额：状态为“已完成”的订单金额合计
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //select sum(amount) from orders where status = ? and order_time < ? and order_time > ?
            Double turnover = orderMapper.getTurnoverByTime(Orders.COMPLETED, beginTime, endTime);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        TurnoverReportVO turnoverReportVO = TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();


        return turnoverReportVO;
    }

    /**
     * 用户统计
     *
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO user(LocalDate begin, LocalDate end) {
        //把begin到end的日期放入dateList集合中
        List<LocalDate> dateList = new ArrayList<>();
        while (begin.isBefore(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        dateList.add(end);

        //查询begin->end每天总的用户数
        List<Long> totalUserList = new ArrayList<>();
        //查询begin->end每天新的用户数
        List<Long> newUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //查询date日期内用户数量
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //select count(id) from user where create_time < ?
            Long totalUser = userMapper.getUserByTime(null, endTime);
            totalUser = totalUser == null ? 0L : totalUser;
            totalUserList.add(totalUser);

            //查询在date日期在小程序创建的用户数量
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);

            //select count(id) from user where create_time < ? and create_time > ?
            Long newlUser = userMapper.getUserByTime(beginTime, endTime);
            newlUser = newlUser == null ? 0L : newlUser;
            newUserList.add(newlUser);
        }

        UserReportVO reportVO = UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();


        return reportVO;
    }

    /**
     * 订单统计
     *
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO orders(LocalDate begin, LocalDate end) {
        //把begin到end的日期放入dateList集合中
        List<LocalDate> dateList = new ArrayList<>();
        while (begin.isBefore(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }
        dateList.add(end);

        //统计每日订单数和每日订单有效数分别存入orderCountList和validOrderCountList中
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //每日订单数
            Integer orderCount = orderMapper.getOrderCountByTime(beginTime, endTime, null);
            orderCount = orderCount == null ? 0 : orderCount;
            orderCountList.add(orderCount);
            //每日有效订单数
            Integer validOrderCount = orderMapper.getOrderCountByTime(beginTime, endTime, Orders.COMPLETED);
            validOrderCount = validOrderCount == null ? 0 : validOrderCount;
            validOrderCountList.add(validOrderCount);
        }
        //订单总数、有效订单数、订单完成率
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        Integer totalOrderCount = orderMapper.getOrderCountByTime(null, endTime, null);
        Integer validOrderCount = orderMapper.getOrderCountByTime(null, endTime, Orders.COMPLETED);
        Double orderCompletionRate = ((double) validOrderCount / totalOrderCount);

        //封装返回对象
        OrderReportVO orderReportVO = OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();

        return orderReportVO;
    }

    /**
     * 查询销量排名top10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {


        //select name from orders o left outer join order_detail d on o.id = d.order_id group by name limit 10;
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getTop10ByTime(beginTime, endTime, Orders.COMPLETED);

        List<String> nameList = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());


        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();

        return salesTop10ReportVO;
    }

}

