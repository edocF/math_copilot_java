package com.fu.math_copilot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fu.math_copilot.model.dto.user.UserAddRequest;
import com.fu.math_copilot.model.dto.user.UserQueryRequest;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.vo.LoginUserVO;
import com.fu.math_copilot.model.vo.UserVO;


import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 *
 * @author <a href="https://github.com/edocF">edocF</a>
 * @from <a href="https://fu.icu">edocF</a>
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     *
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword);



    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param
     * @return
     */
    User getLoginUserPermitNull();

    /**
     * 是否为管理员
     *
     * @param
     * @return
     */
    boolean isAdmin();


    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的已登录用户信息
     * @param user
     * @param token
     * @return
     */
    LoginUserVO getLoginUserVO(User user, String token);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 添加用户
     *
     * @param userAddRequest
     * @return
     */
    User addUser(UserAddRequest userAddRequest);

    /**
     * 添加用户每日签到
     *
     * @param id
     * @return
     */
    Boolean addUserSignIn(Long id);

    /**
     * 获取用户签某年的到信息
     *
     * @param id
     * @param year
     * @return
     */
    List<Integer> getUserSignInRecord(Long id, Integer year);

}
