package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {


    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付，无实现
     * @param ordersPaymentDTO
     */
    void payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 分页查询历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery(int page, int pageSize, Integer status);

    /**
     * 查询订单详情
     * @param orderId
     * @return
     */
    OrderVO getDetail(Long orderId);

    /**
     * 取消订单
     * @param id
     */
    void cancelOrder(Long id);

    /**
     * 再来一单
     * @param id
     */
    void repetitionOrder(Long id);

    /**
     * 订单条件查询
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult orderSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO statusStatistics();

    /**
     * orderConfirm
     * @param ordersConfirmDTO
     */
    void orderConfirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    void orderRejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    void orderCancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单
     * @param id
     * @throws Exception
     */
    void orderDelivery(Long id) throws Exception;

    /**
     * 完成订单
     * @param id
     * @throws Exception
     */
    void orderComplete(Long id) throws Exception;

    /**
     * 用户催单
     * @param id
     */
    void reminderOrder(Long id);
}
