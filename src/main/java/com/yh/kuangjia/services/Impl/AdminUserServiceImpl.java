package com.yh.kuangjia.services.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yh.kuangjia.base.Result;
import com.yh.kuangjia.base.ResultList;
import com.yh.kuangjia.base.TokenAdmin;
import com.yh.kuangjia.base.TokenHelper;
import com.yh.kuangjia.dao.AdminDeptMapper;
import com.yh.kuangjia.dao.AdminRightGroupMapper;
import com.yh.kuangjia.dao.AdminRoleMapper;
import com.yh.kuangjia.entity.AdminDept;
import com.yh.kuangjia.entity.AdminRightGroup;
import com.yh.kuangjia.entity.AdminRole;
import com.yh.kuangjia.entity.AdminUser;
import com.yh.kuangjia.dao.AdminUserMapper;
import com.yh.kuangjia.models.AdminUser.*;
import com.yh.kuangjia.services.AdminUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yh.kuangjia.util.AdapterUtil;
import com.yh.kuangjia.util.DateUtil;
import com.yh.kuangjia.util.IPUtil;
import com.yh.kuangjia.util.UUIDUtil;
import com.yh.kuangjia.util.security.MD5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 任性
 * @since 2019-10-25
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<AdminUserMapper, AdminUser> implements AdminUserService {

    @Autowired
    AdminUserMapper mapper;
    @Autowired
    AdminRightGroupMapper rightGroupMapper;
    @Autowired
    AdminRoleMapper adminRoleMapper;
    @Autowired
    AdminDeptMapper adminDeptMapper;

    @Override
    public Result Login(AdminUserLogin dto) {
        AdminUser user = mapper.selectOne(new QueryWrapper<AdminUser>().eq("user_name", dto.getUser_name()));
        if (null == user || user.getAdmin_id() == 0 || user.getIs_del()) {
            return new Result(Define.INPUT_ERROR, Define.INPUT_ERROR_MSG);
        }
        if (user.getIs_disabled()) {
            return new Result(Define.DISABLED_ERROR, Define.DISABLED_ERROR_MSG);
        }
        String md5 = MD5Util.ToMD5(MessageFormat.format("{0}${1}", dto.getUser_pwd(), user.getPwd_salt()));
        if (!md5.equals(user.getUser_pwd())) {
            //统计登录记录
            return new Result(Define.PWD_ERROR, Define.PWD_ERROR_MSG);
        }
        TokenAdmin token = new TokenAdmin();
        token.setAdminId(user.getAdmin_id());
        token.setRoleIDs(user.getRole_ids());
        //统计登录记录
        LoginUpdate(user.getAdmin_id());
        return Result.success(TokenHelper.GetAccessTokenAdmin(token));
    }

    @Override
    public Result GetAdminUserMenuRightList(int adminid) {
        AdminUser adminUser = mapper.selectById(adminid);
        if (null == adminUser) {
            return new Result(Define.INPUT_ERROR, Define.INPUT_ERROR_MSG);
        }
        boolean isSupper = adminUser.getIs_super();
        String roleids = adminUser.getRole_ids().replaceAll("^,*|,*$", "");
        if (StringUtils.isEmpty(roleids)) {
            return new Result(Define.ROLEIDS_ERROR, Define.ROLEIDS_ERROR_MSG);
        }
        List<AdminUserRightList> rightLists = isSupper ? GetSuperMenuRights() : GetSuperMenuRights(roleids);
        List<Integer> groupids = new ArrayList<>();
        //获取分组ID
        rightLists.forEach(o -> {
            if (!groupids.contains(o.getGroup_id())) {
                groupids.add(o.getGroup_id());
            }
        });
        List<AdminUserGroupList> groupList = new ArrayList<>();
        //根据分组ID获取集合
        List<AdminRightGroup> adminRightGroups = rightGroupMapper.selectList(new QueryWrapper<AdminRightGroup>());
        Collections.sort(adminRightGroups, (AdminRightGroup h1, AdminRightGroup h2) -> h2.getSort() - h1.getSort());
        adminRightGroups.forEach(o -> {
            if (groupids.contains(o.getGroup_id())) {//分组
                AdminUserGroupList group = new AdminUserGroupList(o.getPath(), o.getIcon(), o.getRouter_name(), o.getGroup_name(), new ArrayList<>());
                rightLists.forEach(p -> {
                    if (p.getGroup_id() == o.getGroup_id()) {//菜单
                        group.getChildren().add(new AdminUserGroupRightList(p.getPath(), p.getIcon(), p.getName(), p.getTitle(), p.getComponent()));
                    }
                });
                groupList.add(group);
            }
        });
        return Result.success(groupList);
    }

    @Override
    public Result getUserInfo(int adminid) {
        AdminUser adminUser = mapper.selectById(adminid);
        AdminUserInfoList adapter = AdapterUtil.Adapter(adminUser, AdminUserInfoList.class);
        adapter.setRole_names(new ArrayList<>());
        if (null != adminUser.getRole_ids()) {
            List<String> list = Arrays.asList(adminUser.getRole_ids().split(","));
            list.forEach(o -> {
                AdminRole adminRole = adminRoleMapper.selectOne(new QueryWrapper<AdminRole>().eq("role_id", o));
                adapter.getRole_names().add(adminRole.getRole_name());
            });
        }
        return Result.success(adapter);
    }

    @Override
    public ResultList getUserList(AdminUserFilter filter) {
        List<AdminDept> adminDepts = adminDeptMapper.selectList(new QueryWrapper<AdminDept>());
        QueryWrapper<AdminUser> queryWrapper = new QueryWrapper<>();
        if (filter.getField().equals(1) && null != filter.getKeyword() && !filter.getKeyword().equals("")) {
            queryWrapper.eq("user_name", filter.getKeyword());
        }
        if (null != filter.getDept_id() && !filter.getDept_id().equals("") && !filter.getDept_id().equals(0)) {
            queryWrapper.eq("dept_id", filter.getDept_id());
        }
        if (filter.getTime_field().equals(1) && null != filter.getBegin_date() && null != filter.getEnd_date() && !filter.getEnd_date().equals(0) && !filter.getBegin_date().equals(0)) {
            queryWrapper.ge("last_login_date", filter.getBegin_date());
            queryWrapper.le("last_login_date", filter.getEnd_date());
        }
        IPage<AdminUser> adminUserIPage = mapper.selectPage(new Page<>(filter.getPage_index(), filter.getPage_size()), queryWrapper);
        List<AdminUserList> adapter = AdapterUtil.Adapter(adminUserIPage.getRecords(), AdminUserList.class);
        adapter.parallelStream().forEach(o -> {
            adminDepts.parallelStream().filter(f -> f.getDept_id().equals(o.getDept_id())).forEach(o3 -> {
                o.setDept_name(o3.getDept_name());
            });
            o.setRole_name_array(new ArrayList());
            o.setRole_id_array(new ArrayList());
            if (null != o.getRole_ids()) {
                List<String> list = Arrays.asList(o.getRole_ids().split(","));
                list.forEach(o1 -> {
                    AdminRole adminRole = adminRoleMapper.selectOne(new QueryWrapper<AdminRole>().eq("role_id", o1));
                    o.getRole_name_array().add(adminRole.getRole_name());
                    o.getRole_id_array().add(Integer.parseInt(o1));
                });
            }
        });
        return ResultList.success(adapter, adminUserIPage.getTotal());
    }

    @Override
    public Result addUser(Integer adminId, AdminUserAdd adminUserAdd) {
        String user_pwd = adminUserAdd.getUser_pwd();
        String uuid = UUIDUtil.getUuid();
        String md5Pas = MD5Util.ToMD5(MessageFormat.format("{0}${1}", user_pwd, uuid));
        adminUserAdd.setUser_pwd(md5Pas);
        String s = Arrays.toString(adminUserAdd.getRole_id_array());
        String substring = s.substring(1, s.length() - 1);
        adminUserAdd.setRole_ids(substring);
        adminUserAdd.setPwd_salt(uuid);
        adminUserAdd.setCreate_date(DateUtil.GetDateInt());
        adminUserAdd.setCreate_time(DateUtil.GetDate());
        if (adminUserAdd.getIs_del() == null) {
            adminUserAdd.setIs_del(false);
        }
        if (adminUserAdd.getIs_super() == null) {
            adminUserAdd.setIs_super(false);
        }
        int c = 0;
        c++;
        adminUserAdd.setLogin_times(c);
        adminUserAdd.setCreate_time(DateUtil.GetDate());
        if (mapper.insert(adminUserAdd)==0)return new Result(1, "error");
        adminUserAdd.setAdmin_id(adminUserAdd.getAdmin_id());
        return Result.success();
    }

    private List<AdminUserRightList> GetSuperMenuRights() {
        return mapper.GetSuperMenuRights();
    }

    private List<AdminUserRightList> GetSuperMenuRights(String roleids) {
        return mapper.GetAdminUserMenuRights(roleids);
    }

    public Result LoginUpdate(int adminid) {
        AdminUser user = mapper.selectById(adminid);
        user.setLast_login_date(DateUtil.GetDateInt());
        user.setLast_login_time(DateUtil.GetDate());
        user.setLast_login_ip(IPUtil.getIpAddr());
        user.setLogin_times(user.getLogin_times() + 1);
        if (mapper.updateById(user) > 0)
            return Result.success();
        return Result.failure();
    }

    /**
     * 业务错误代码定义
     */
    private static class Define {
        public static Integer INPUT_ERROR = 1;
        public static String INPUT_ERROR_MSG = "用户名不存在";

        public static Integer DISABLED_ERROR = 1;
        public static String DISABLED_ERROR_MSG = "用户已被禁用，无法登录";

        public static Integer PWD_ERROR = 1;
        public static String PWD_ERROR_MSG = "密码输入错误";

        public static Integer ROLEIDS_ERROR = 1;
        public static String ROLEIDS_ERROR_MSG = "用户角色为空";
    }
}
