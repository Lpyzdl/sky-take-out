package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

}
