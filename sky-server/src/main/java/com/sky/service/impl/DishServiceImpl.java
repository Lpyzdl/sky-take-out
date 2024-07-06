package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.DishDisableFailedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetMealDishMapper;
import com.sky.mapper.SetMealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.sky.constant.MessageConstant.DISH_BE_RELATED_BY_ONSALE_SETMEAL;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetMealDishMapper setMealDishMapper;

    @Autowired
    private SetMealMapper setMealMapper;

    /**
     * 新增菜品和对应的口味数据
     * @param dishDTO
     */
    //涉及多个数据表：dish和flavor添加事务注解
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO){

        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);

        //向菜品表插入1条数据
        dishMapper.insert(dish);

        Long dishId = dish.getId();

        //向口味表插入n条数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors != null && flavors.size() > 0){
            for(DishFlavor f : flavors){
                f.setDishId(dishId);
            }
            dishFlavorMapper.insertBatch(flavors);
        }

    }

    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 菜品批量删除
     * @param ids
     */
    @Transactional
    public void deleteBacth(List<Long> ids) {
        //判断商品是否能够删除---是否存在起售中的商品？？
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish.getStatus() == StatusConstant.ENABLE){
                //当前菜品起售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断商品是否能够删除---是否被套餐关联？？
        List<Long> setmealIds = setMealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0){
            //当前菜品关联了套餐，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //删除菜品表中的菜品数据
        /*for (Long id : ids) {
            dishMapper.deleteById(id);
            //删除菜品关联的口味数据
            dishFlavorMapper.deleteByDishId(id);
        }*/

        //根据菜品id集合批量删除菜品数据
        //sql: delete from dish where id in (?,?,?)
        dishMapper.deleteByIds(ids);
        //根据菜品id集合批量删除关联的口味数据
        //sql: delete from dish_flavor where dish_id in (?,?,?)
        dishFlavorMapper.deleteByDishIds(ids);

    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id) {
        DishVO dishVO = new DishVO();
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);
        //根据dishId查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        //将查询数据封装到dishVO
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 修改菜品信息
     * @param dishDTO
     */
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        //修改菜品表信息
        dishMapper.update(dish);

        //修改菜品关联的口味信息：先删除再插入新数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors != null && flavors.size() > 0){
            flavors.forEach(dishFlavor -> {
                //需要设置dishId，因为口味可能是没加过的，可能没有dishID
                dishFlavor.setDishId(dish.getId());
            });
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 根据类型id获取菜品信息
     * @param categoryId
     * @return
     */
    public List<Dish> getByCategoryId(Long categoryId) {

        Dish dish = Dish.builder().categoryId(categoryId)
                                    .status(StatusConstant.ENABLE)
                                    .build();

        return dishMapper.list(dish);
    }

    /**
     * 设置菜品启用禁用
     * @param status
     * @param id
     */
    public void setStartOrStop(Integer status, Long id) {
        //如果为禁用，查看菜品关联的套餐信息，如果套餐启用则无法修改
        if(status == StatusConstant.DISABLE){
            List<Setmeal> setmeals = setMealMapper.getByDishId(id);
            if (setmeals != null && setmeals.size() > 0){
                setmeals.forEach(setmeal -> {
                    if (setmeal.getStatus() == StatusConstant.ENABLE){
                        throw new DishDisableFailedException(DISH_BE_RELATED_BY_ONSALE_SETMEAL + "。套餐为：" +setmeal.getName());
                    }
                });

            }
        }

        Dish dish = Dish.builder().id(id).status(status).build();

        dishMapper.update(dish);
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
