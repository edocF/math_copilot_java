package com.fu.math_copilot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fu.math_copilot.common.ErrorCode;
import com.fu.math_copilot.constant.CommonConstant;
import com.fu.math_copilot.constant.RedisConstant;
import com.fu.math_copilot.exception.BusinessException;
import com.fu.math_copilot.exception.ThrowUtils;
import com.fu.math_copilot.mapper.UserMapper;
import com.fu.math_copilot.model.dto.user.UserAddRequest;
import com.fu.math_copilot.model.dto.user.UserQueryRequest;
import com.fu.math_copilot.model.entity.User;
import com.fu.math_copilot.model.enums.UserRoleEnum;
import com.fu.math_copilot.model.vo.LoginUserVO;
import com.fu.math_copilot.model.vo.UserVO;
import com.fu.math_copilot.service.UserService;
import com.fu.math_copilot.utils.JwtUtils;
import com.fu.math_copilot.utils.SqlUtils;
import com.fu.math_copilot.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.fu.math_copilot.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现
 *
 * @author <a href="https://github.com/edocF">edocF</a>
 * @from <a href="https://fu.icu">edocF</a>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedissonClient redissonClient;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "账号重复");
            // 2. 加密
            String encryptPassword = passwordEncoder.encode(userPassword);
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "注册失败，请重试");
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        // 查询用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(User::getUserAccount, userAccount);
        User user = this.getOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        if(!passwordEncoder.matches(userPassword, user.getUserPassword()))
        {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 3. 记录用户的登录态
        String jwtToken = jwtUtils.createToken(user.getId());
        return this.getLoginUserVO(user, jwtToken);
    }



    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param
     * @return
     */
    @Override
    public User getLoginUserPermitNull() {
        // 先判断是否已登录

        User currentUser = getById(UserContext.getCurrentUserId());
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     *
     * @return
     */
    @Override
    public boolean isAdmin() {
        // 仅管理员可查询
        User user = getById(UserContext.getCurrentUserId());
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user, String token) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        loginUserVO.setToken(token);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public User addUser(UserAddRequest userAddRequest) {
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = passwordEncoder.encode(defaultPassword);
        user.setUserPassword(encryptPassword);
        boolean result = save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user;
    }

    @Override
    public Boolean addUserSignIn(Long id) {
        LocalDate date = LocalDate.now();
        int year = date.getYear();
        int dayOfYear = date.getDayOfYear();
        //拿到redis的key
        String key = RedisConstant.getUserSingInRedisKey(year,id);
        //拿到redis的bitSet
        RBitSet bitSet = redissonClient.getBitSet(key);
        //判断今天是否签到
        if (!bitSet.get(dayOfYear))
        {   //签到
            bitSet.set(dayOfYear,true);
        }
        //当天已签到
        return true;
    }

    @Override
    public List<Integer> getUserSignInRecord(Long id, Integer year) {
        String Key = RedisConstant.getUserSingInRedisKey(year,id);
        //加载bitSet到内存，避免每次查询都从redis中加载
        BitSet bitSet = redissonClient.getBitSet(Key).asBitSet();
        //获取签到记录
        List<Integer> dayList = new ArrayList<>();
        //从0开始，遍历bitSet，获取所有已签到天数
        int index = bitSet.nextSetBit(0);
        while (index != -1)
        {
            dayList.add(index);
            index = bitSet.nextSetBit(index+1);
        }
        return dayList;
    }
}
