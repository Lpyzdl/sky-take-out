package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
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
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
}
