package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Api(tags = "订单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    OrderService orderService;

    /**
     * 订单条件查询
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单条件查询")
    public Result<PageResult> orderSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("订单条件查询: {}", ordersPageQueryDTO);

        PageResult pageResult = orderService.orderSearch(ordersPageQueryDTO);

        return Result.success(pageResult);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @GetMapping("/statistics")
    @ApiOperation("各个状态的订单数量统计")
    public Result<OrderStatisticsVO> orderStatistics(){

        log.info("各个状态的订单数量统计");

        OrderStatisticsVO orderStatisticsVO = orderService.statusStatistics();

        return Result.success(orderStatisticsVO);
    }

    /**
     * 获取订单详情
     * @param id
     * @return
     */
    @GetMapping("/details/{id}")
    @ApiOperation("获取订单详情")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id){

        log.info("获取订单详情: {}", id);

        OrderVO orderVO = orderService.getDetail(id);

        return Result.success(orderVO);

    }

    /**
     * 接单
     * @param ordersConfirmDTO
     * @return
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result orderConfirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO){
        log.info("接单: {}", ordersConfirmDTO);

        orderService.orderConfirm(ordersConfirmDTO);

        return Result.success();
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result orderRejection (@RequestBody OrdersRejectionDTO ordersRejectionDTO) throws Exception{
        log.info("拒单：{}", ordersRejectionDTO);

        orderService.orderRejection(ordersRejectionDTO);

        return Result.success();
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     * @return
     * @throws Exception
     */
    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result orderCancel(@RequestBody OrdersCancelDTO ordersCancelDTO) throws Exception{
        log.info("取消订单: {}",ordersCancelDTO);
        orderService.orderCancel(ordersCancelDTO);

        return Result.success();
    }

    /**
     * 派送订单
     * @param id
     * @return
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result orderDelivery(@PathVariable Long id) throws Exception{
        log.info("派送订单: {}", id);
        orderService.orderDelivery(id);

        return Result.success();
    }

    /**
     * 完成订单
     * @param id
     * @return
     */
    @PutMapping("complete/{id}")
    @ApiOperation("完成订单")
    public Result orderComplete(@PathVariable Long id) throws Exception{
        log.info("完成订单: {}", id);
        orderService.orderComplete(id);

        return Result.success();
    }
}
