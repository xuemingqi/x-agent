package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkFolderDo;

import java.util.List;

public interface KnowledgeLinkFolderManagementService {

    /**
     * 获取指定用户的个人文件夹，返回平铺的父子关系，不向页面返回公共目录。
     *
     * @param userId 当前用户 ID
     * @return 可见文件夹列表
     */
    List<KnowledgeLinkFolderDo> list(String userId);

    /**
     * 在指定用户的个人目录下创建文件夹，未指定父目录时使用根目录。
     *
     * @param userId   当前用户 ID
     * @param parentId 个人父文件夹 ID，null 或 0 表示根目录
     * @param name     文件夹名称，同级目录内不可重复
     * @return 已创建的文件夹
     */
    KnowledgeLinkFolderDo create(String userId, Long parentId, String name);

    /**
     * 重命名指定用户拥有的个人文件夹，不改变其父子关系。
     *
     * @param userId 当前用户 ID
     * @param id     个人文件夹 ID
     * @param name   新文件夹名称
     * @return 更新后的文件夹
     */
    KnowledgeLinkFolderDo rename(String userId, Long id, String name);

    /**
     * 将个人文件夹及其内容移到指定父目录，不改变名称；不能移到自身、后代或其他用户目录。
     *
     * @param userId   当前用户 ID
     * @param id       待移动文件夹 ID，不允许移动根目录
     * @param parentId 目标个人父文件夹 ID，0 表示根目录；目标目录下不可存在同名文件夹
     * @return 移动后的文件夹
     */
    KnowledgeLinkFolderDo move(String userId, Long id, Long parentId);

    /**
     * 删除指定用户拥有的空文件夹，不级联删除文档或子目录。
     *
     * @param userId 当前用户 ID
     * @param id     个人文件夹 ID，不允许删除根目录
     * @return 是否删除成功
     */
    boolean delete(String userId, Long id);

    /**
     * 在调用方事务内验证并锁定个人目标目录，防止创建或移动文档时目录被并发删除。
     * 根目录无需加锁；不允许传入公共或其他用户的文件夹 ID。
     *
     * @param userId 当前用户 ID
     * @param id     目标文件夹 ID，null 或 0 表示无需验证具体目录
     */
    void lockOwned(String userId, Long id);

    /**
     * 查询公共在线知识库文件夹。
     */
    List<KnowledgeLinkFolderDo> listPublic();

    /**
     * 创建公共在线知识库文件夹。
     */
    KnowledgeLinkFolderDo createPublic(String actorUserId, Long parentId, String name);

    /**
     * 重命名公共在线知识库文件夹。
     */
    KnowledgeLinkFolderDo renamePublic(Long id, String name);

    /**
     * 删除公共在线知识库空文件夹。
     */
    boolean deletePublic(Long id);

    /**
     * 在写事务中验证并锁定公共目标目录。
     */
    void lockPublic(Long id);
}
