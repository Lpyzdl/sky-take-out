package com.sky.task;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
//    @Scheduled(cron = "2/5 * * * * ?")//测试
    public void processTimeoutOrder(){
        log.info("处理超时订单: {}", LocalDateTime.now());

        LocalDateTime orderTime = LocalDateTime.now().plusMinutes(-15);

        //select * from orders where status = ? and order_time < (当前时间 - 15min)
        List<Long> ids = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, orderTime);

        if (ids != null && ids.size() > 0){
            for (Long id : ids) {
                Orders orders = new Orders();
                orders.setId(id);
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelTime(LocalDateTime.now());
                orders.setCancelReason("订单超时，自动取消");
                orderMapper.updateStatus(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中订单
     */
    @Scheduled(cron = "0 0 2 * * ?")//每天两点触发
//    @Scheduled(cron = "0/5 * * * * ?")//测试 5s一次
    public void processDeliveryOrder(){
        log.info("定时处理一直处于派送中订单: {}", LocalDateTime.now());

        //处理前一天的订单
        LocalDateTime orderTime = LocalDateTime.now().plusHours(-2);

        //查询处于派送中并且是昨天下单的订单
        List<Long> ids = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, orderTime);
        if (ids != null && ids.size() > 0){
            for (Long id : ids) {
                Orders orders = new Orders();
                orders.setId(id);
                orders.setStatus(Orders.COMPLETED);
            }
        }
    }

}
