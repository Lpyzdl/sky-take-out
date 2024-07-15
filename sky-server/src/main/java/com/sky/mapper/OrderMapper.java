package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.List;


@Mapper
public interface OrderMapper {


    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 查询订单
     * @param orderNumber
     * @param userId
     * @return
     */
    @Select("select * from orders where user_id = #{userId} and number = #{orderNumber}")
    Orders getOrder(String orderNumber, Long userId);

    /**
     * 更新订单状态
     * @param orders
     */
    void updateStatus(Orders orders);

    /**
     * 分页查询历史订单
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据用户id和订单id查询订单
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 获取相关状态的订单数量
     * @param status
     * @return
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer getCountByStatus(Integer status);

    /**
     * 根据订单状态和下单时间查询订单
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select orders.id from orders where status = #{status} and order_time < #{orderTime}")
    List<Long> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 查询date日期对应的营业额：状态为“已完成”的订单金额合计
     * @param status
     * @param beginTime
     * @param endTime
     * @return
     */
    Double getTurnoverByTime(Integer status, LocalDateTime beginTime, LocalDateTime endTime);

}
