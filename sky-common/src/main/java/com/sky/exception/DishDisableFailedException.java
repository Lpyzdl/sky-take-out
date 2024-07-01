package com.sky.exception;

/**
 * 套餐启用失败异常
 */
public class DishDisableFailedException extends BaseException {

    public DishDisableFailedException(){}

    public DishDisableFailedException(String msg){
        super(msg);
    }
}
