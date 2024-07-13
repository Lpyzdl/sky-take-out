package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //1.处理各种业务异常（地址簿为空，购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null){
            //抛出地址为空业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.size() == 0){
            //抛出购物车无数据异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2.向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orders.setAddress(addressBook.getDetail());

        orderMapper.insert(orders);


        List<OrderDetail> orderDetailList = new ArrayList<>();
        //3.向订单明细表插入n条数据

        Long ordersId = orders.getId();

        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(ordersId);//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //4.清空当前购物车数据

        shoppingCartMapper.deleteByUserId(userId);

        //5.封装VO返回结果

        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(ordersId)
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付，无实现
     * @param ordersPaymentDTO
     */
    public void payment(OrdersPaymentDTO ordersPaymentDTO) {
        Long userId = BaseContext.getCurrentId();
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        Orders orders = orderMapper.getOrder(orderNumber, userId);

        orders.setPayMethod(ordersPaymentDTO.getPayMethod());
        orders.setPayStatus(Orders.PAID);
        orders.setStatus(Orders.TO_BE_CONFIRMED);
        orders.setCheckoutTime(LocalDateTime.now());

        orderMapper.updateStatus(orders);

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type", 1);//1来单提醒 2客户催单
        map.put("orderId", orders.getId());
        map.put("content", "订单号：" + orderNumber);

        String json = JSON.toJSONString(map);

        //推送消息
        webSocketServer.sendToAllClient(json);

    }

    /**
     * 分页查询历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery(int page, int pageSize, Integer status) {

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();

        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        PageHelper.startPage(page, pageSize);
        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if (ordersPage != null && ordersPage.size() != 0){
            for (Orders order : ordersPage) {
                Long orderId = order.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);

                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }


        return new PageResult(ordersPage.getTotal(), list);
    }

    /**
     * 查询订单详情
     * @param orderId
     * @return
     */
    public OrderVO getDetail(Long orderId) {

        //查询订单
        Orders orders = orderMapper.getById(orderId);

        //查询订单详情
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetails);

        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     */
    public void cancelOrder(Long id) {

        //查询订单
        Orders orders = orderMapper.getById(id);

        //订单为空抛异常
        if (orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态不能取消时
        if (orders.getStatus() > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders ordersNew = new Orders();
        ordersNew.setId(orders.getId());

        //订单已支付时
        if (orders.getPayStatus() == Orders.PAID){
            ordersNew.setPayStatus(Orders.REFUND);
        }

        ordersNew.setStatus(Orders.CANCELLED);
        ordersNew.setCancelTime(LocalDateTime.now());
        ordersNew.setCancelReason("用户取消");

        orderMapper.updateStatus(ordersNew);
    }

    /**
     * 再来一单
     * @param id
     */
    public void repetitionOrder(Long id) {


        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
        //将商品信息添加到购物车

        List<ShoppingCart> shoppingCarts = new ArrayList<>();

        for (OrderDetail orderDetail : orderDetails) {

            ShoppingCart shoppingCart = ShoppingCart.builder()
                    .image(orderDetail.getImage())
                    .name(orderDetail.getName())
                    .dishId(orderDetail.getDishId())
                    .setmealId(orderDetail.getDishId())
                    .dishFlavor(orderDetail.getDishFlavor())
                    .number(orderDetail.getNumber())
                    .amount(orderDetail.getAmount())
                    .image(orderDetail.getImage())
                    .build();
            shoppingCart.setUserId(BaseContext.getCurrentId());

            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCarts.add(shoppingCart);
        }

        shoppingCartMapper.insertBatch(shoppingCarts);

    }

    /**
     * 订单条件查询
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult orderSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();

        if (ordersPage != null && ordersPage.size() != 0){
            for (Orders orders : ordersPage) {
                Long orderId = orders.getId();

                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
                String orderDishes = "";
                for (OrderDetail orderDetail : orderDetails) {
                    orderDishes += orderDetail.getName() + orderDetail.getDishFlavor()
                            + "×" +orderDetail.getNumber() + " 价格：" + orderDetail.getAmount() + "  ";
                }

                OrderVO orderVO = new OrderVO();
                orderVO.setOrderDishes(orderDishes);
                BeanUtils.copyProperties(orders, orderVO);

                list.add(orderVO);
            }
        }
        return new PageResult(ordersPage.getTotal(), list);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statusStatistics() {

        Integer toBeConfirmed = orderMapper.getCountByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.getCountByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.getCountByStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = OrderStatisticsVO.builder()
                                                .confirmed(confirmed)
                                                .toBeConfirmed(toBeConfirmed)
                                                .deliveryInProgress(deliveryInProgress)
                                                .build();

        return orderStatisticsVO;
    }

    /**
     * orderConfirm
     * @param ordersConfirmDTO
     */
    public void orderConfirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.updateStatus(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    public void orderRejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception{

        //只有处于“待接单”状态并且存在的订单才能拒单
        Orders orders1 = orderMapper.getById(ordersRejectionDTO.getId());
        if (orders1 == null || orders1.getStatus() != Orders.TO_BE_CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason("商家取消")
                .build();

        if (orders1.getPayStatus() == Orders.PAID){
            orders.setPayStatus(Orders.REFUND);
        }

        orderMapper.updateStatus(orders);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     * @throws Exception
     */
    public void orderCancel(OrdersCancelDTO ordersCancelDTO) throws Exception {

        Orders orders = new Orders();

        //判断订单是否已支付
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        if (ordersDB.getPayMethod() == Orders.PAID){
            orders.setPayStatus(Orders.REFUND);
        }

        orders.setId(ordersCancelDTO.getId());
        orders.setCancelTime(LocalDateTime.now());
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setStatus(Orders.CANCELLED);

        orderMapper.updateStatus(orders);

    }

    /**
     * 派送订单
     * @param id
     * @throws Exception
     */
    public void orderDelivery(Long id) throws Exception {

        //只有处于CONFIRMED才能点击派送订单
        Orders ordersDB = orderMapper.getById(id);

        if (ordersDB == null || ordersDB.getStatus() != Orders.CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.updateStatus(orders);
    }

    /**
     * 完成订单
     * @param id
     * @throws Exception
     */
    public void orderComplete(Long id) throws Exception {

        //只有在派送中的商品才可以点击完成订单
        Orders ordersDB = orderMapper.getById(id);

        if (ordersDB == null || ordersDB.getStatus() != Orders.DELIVERY_IN_PROGRESS){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();

        orders.setId(id);
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.updateStatus(orders);
    }

    public void reminderOrder(Long id){

        Orders orders = orderMapper.getById(id);

        //判断订单是否存在
        if (orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type", 2);//1来单提醒 2客户催单
        map.put("orderId", id);
        map.put("content", "订单号：" + orders.getNumber());

        String json = JSON.toJSONString(map);

        //推送消息
        webSocketServer.sendToAllClient(json);

    }
}
