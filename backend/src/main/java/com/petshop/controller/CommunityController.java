package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.petshop.common.Response;
import com.petshop.entity.CommunityPost;
import com.petshop.entity.CommunityPostComment;
import com.petshop.entity.CommunityPostImage;
import com.petshop.entity.CommunityPostLike;
import com.petshop.entity.User;
import com.petshop.mapper.CommunityPostCommentMapper;
import com.petshop.mapper.CommunityPostImageMapper;
import com.petshop.mapper.CommunityPostLikeMapper;
import com.petshop.mapper.CommunityPostMapper;
import com.petshop.mapper.UserMapper;
import com.petshop.service.UserService;
import com.petshop.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物社区：图文动态 + 点赞 + 评论。
 * 浏览对未登录用户开放（GET 放行），发帖/点赞/评论必须登录。
 */
@RestController
@RequestMapping("/community")
public class CommunityController {

    private static final Logger log = LoggerFactory.getLogger(CommunityController.class);

    /** 单条动态最多 6 张图 */
    private static final int MAX_IMAGES = 6;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_COMMENT_LENGTH = 500;
    /** 列表单页上限，避免客户端传大值把图床拉爆 */
    private static final int MAX_PAGE_SIZE = 20;
    /** 可见范围：0 公开 / 1 仅自己可见 */
    private static final int VISIBILITY_PUBLIC = 0;
    private static final int VISIBILITY_PRIVATE = 1;
    /** 搜索关键词最长限制，避免超长 LIKE 拖慢查询 */
    private static final int MAX_KEYWORD_LENGTH = 50;

    @Autowired
    private CommunityPostMapper postMapper;

    @Autowired
    private CommunityPostImageMapper imageMapper;

    @Autowired
    private CommunityPostLikeMapper likeMapper;

    @Autowired
    private CommunityPostCommentMapper commentMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    // ---------------- 动态列表 ----------------

    /** 社区广场：除了别人设为仅自己可见的动态，其余全部可见 */
    @GetMapping("/posts")
    public Response<List<PostItem>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(value = "keyword", required = false) String keyword,
                                        Authentication authentication) {
        return Response.success(queryPosts(page, size, null, AuthUtils.resolveUserId(authentication), keyword));
    }

    /** 我的动态：只返回自己发布的（含仅自己可见） */
    @GetMapping("/posts/mine")
    public Response<List<PostItem>> myPosts(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        return Response.success(queryPosts(page, size, userId, userId, keyword));
    }

    private List<PostItem> queryPosts(int page, int size, Integer authorId, Integer viewerId, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<CommunityPost>()
                .eq(CommunityPost::getStatus, 1)
                .orderByDesc(CommunityPost::getId);
        if (authorId != null) {
            wrapper.eq(CommunityPost::getUserId, authorId);
        } else if (viewerId != null) {
            // 广场里自己设成私密的动态也要能看到，别人的私密动态则过滤掉
            wrapper.and(w -> w.eq(CommunityPost::getVisibility, VISIBILITY_PUBLIC)
                    .or().eq(CommunityPost::getUserId, viewerId));
        } else {
            wrapper.eq(CommunityPost::getVisibility, VISIBILITY_PUBLIC);
        }

        String kw = keyword == null ? "" : keyword.trim();
        if (kw.length() > MAX_KEYWORD_LENGTH) {
            kw = kw.substring(0, MAX_KEYWORD_LENGTH);
        }
        if (!kw.isEmpty()) {
            // 同时命中「帖子内容」与「作者用户名」，作者匹配靠先查用户 ID 再 IN
            List<Integer> authorIds = userMapper.searchIdsByUsername(kw);
            final String like = kw;
            wrapper.and(w -> {
                w.like(CommunityPost::getContent, like);
                if (authorIds != null && !authorIds.isEmpty()) {
                    w.or().in(CommunityPost::getUserId, authorIds);
                }
            });
        }

        PageHelper.startPage(safePage, safeSize);
        List<CommunityPost> posts = postMapper.selectList(wrapper);
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Integer> likedIds = new HashSet<>();
        if (viewerId != null) {
            List<Integer> postIds = new ArrayList<>();
            for (CommunityPost post : posts) {
                postIds.add(post.getId());
            }
            List<CommunityPostLike> likes = likeMapper.selectList(new LambdaQueryWrapper<CommunityPostLike>()
                    .eq(CommunityPostLike::getUserId, viewerId)
                    .in(CommunityPostLike::getPostId, postIds));
            for (CommunityPostLike like : likes) {
                likedIds.add(like.getPostId());
            }
        }

        Map<Integer, User> authorCache = new HashMap<>();
        List<PostItem> result = new ArrayList<>();
        for (CommunityPost post : posts) {
            PostItem item = new PostItem();
            item.setId(post.getId());
            item.setUserId(post.getUserId());
            item.setContent(post.getContent());
            item.setImageCount(post.getImageCount() != null ? post.getImageCount() : 0);
            item.setLikeCount(post.getLikeCount() != null ? post.getLikeCount() : 0);
            item.setCommentCount(post.getCommentCount() != null ? post.getCommentCount() : 0);
            item.setLiked(likedIds.contains(post.getId()));
            item.setMine(viewerId != null && viewerId.equals(post.getUserId()));
            item.setVisibility(post.getVisibility() == null ? VISIBILITY_PUBLIC : post.getVisibility());
            item.setCreatedAt(post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
            // 配图改过之后图片仍占同一个 index，客户端用 updatedAt 做缓存版本号
            item.setUpdatedAt(post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : "");

            User author = findAuthor(post.getUserId(), authorCache);
            item.setUserName(displayName(author));
            item.setHasAvatar(hasAvatar(author));
            result.add(item);
        }
        return result;
    }

    // ---------------- 发帖 / 删帖 ----------------

    /** 发布动态：文字必填，图片最多 6 张，visibility 可指定可见范围 */
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Response<Integer> create(@RequestParam(value = "content", required = false) String content,
                                    @RequestParam(value = "images", required = false) MultipartFile[] images,
                                    @RequestParam(value = "visibility", defaultValue = "0") int visibility,
                                    Authentication authentication) throws IOException {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return Response.error(400, "说点什么再发布吧");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH);
        }

        // 先把图片全部读进内存，任何一张读取失败都直接拒绝，避免发出半截帖子
        List<String> contentTypes = new ArrayList<>();
        List<byte[]> imageBytes = new ArrayList<>();
        if (images != null) {
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return Response.error(400, "只能上传图片文件");
                }
                contentTypes.add(contentType);
                imageBytes.add(file.getBytes());
                if (imageBytes.size() >= MAX_IMAGES) {
                    break;
                }
            }
        }

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setContent(text);
        post.setImageCount(imageBytes.size());
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        post.setVisibility(normalizeVisibility(visibility));
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);

        for (int i = 0; i < imageBytes.size(); i++) {
            CommunityPostImage image = new CommunityPostImage();
            image.setPostId(post.getId());
            image.setSortOrder(i);
            image.setContentType(contentTypes.get(i));
            image.setImageData(imageBytes.get(i));
            imageMapper.insert(image);
        }
        log.info("发布社区动态: postId={}, userId={}, 图片数={}", post.getId(), userId, imageBytes.size());
        return Response.success("发布成功", post.getId());
    }

    /** 编辑自己的动态：改文字、改可见范围；配图走单独的 updateImages 接口 */
    @PutMapping("/posts/{id}")
    public Response<Void> update(@PathVariable Integer id,
                                 @RequestBody(required = false) Map<String, Object> payload,
                                 Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        CommunityPost post = postMapper.selectById(id);
        if (post == null) {
            return Response.error(404, "动态不存在或已删除");
        }
        if (!userId.equals(post.getUserId())) {
            return Response.error(403, "只能编辑自己发布的动态");
        }

        Object rawContent = payload == null ? null : payload.get("content");
        Object rawVisibility = payload == null ? null : payload.get("visibility");

        String text = rawContent == null ? post.getContent() : rawContent.toString().trim();
        if (text == null || text.isEmpty()) {
            return Response.error(400, "说点什么再保存吧");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH);
        }

        Integer visibility = post.getVisibility() == null ? VISIBILITY_PUBLIC : post.getVisibility();
        if (rawVisibility != null) {
            try {
                visibility = normalizeVisibility(Integer.parseInt(rawVisibility.toString().trim()));
            } catch (NumberFormatException e) {
                return Response.error(400, "可见范围不合法");
            }
        }

        post.setContent(text);
        post.setVisibility(visibility);
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        log.info("编辑社区动态: postId={}, userId={}, 可见范围={}", id, userId, visibility);
        return Response.success(visibility == VISIBILITY_PRIVATE ? "已设为仅自己可见" : "已保存", null);
    }

    /**
     * 编辑配图：keepIndexes 是保留的旧图序号（逗号分隔，按展示顺序），images 是新增图片；
     * 两者合计最多 6 张，keepIndexes 传空串即清空全部配图。
     */
    @PutMapping(value = "/posts/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public Response<Integer> updateImages(@PathVariable Integer id,
                                         @RequestParam(value = "keepIndexes", required = false) String keepIndexes,
                                         @RequestParam(value = "images", required = false) MultipartFile[] images,
                                         Authentication authentication) throws IOException {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        CommunityPost post = postMapper.selectById(id);
        if (post == null) {
            return Response.error(404, "动态不存在或已删除");
        }
        if (!userId.equals(post.getUserId())) {
            return Response.error(403, "只能编辑自己发布的动态");
        }

        // sort_order 就是客户端访问的 /images/{index}，按它取旧图
        List<CommunityPostImage> oldImages = imageMapper.selectList(new LambdaQueryWrapper<CommunityPostImage>()
                .eq(CommunityPostImage::getPostId, id)
                .orderByAsc(CommunityPostImage::getSortOrder));

        List<CommunityPostImage> kept = new ArrayList<>();
        if (keepIndexes == null) {
            // 不传表示不删旧图，只追加
            kept.addAll(oldImages);
        } else {
            Set<Integer> seen = new HashSet<>();
            for (String token : keepIndexes.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int index;
                try {
                    index = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (index < 0 || index >= oldImages.size() || !seen.add(index)) {
                    continue;
                }
                kept.add(oldImages.get(index));
            }
        }

        List<String> newTypes = new ArrayList<>();
        List<byte[]> newBytes = new ArrayList<>();
        if (images != null) {
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return Response.error(400, "只能上传图片文件");
                }
                if (kept.size() + newBytes.size() >= MAX_IMAGES) {
                    return Response.error(400, "最多只能放 " + MAX_IMAGES + " 张图片");
                }
                newTypes.add(contentType);
                newBytes.add(file.getBytes());
            }
        }

        // 整表重建，保证 sort_order 从 0 连续排列，被删掉的旧图一并清理
        imageMapper.delete(new LambdaQueryWrapper<CommunityPostImage>().eq(CommunityPostImage::getPostId, id));
        int order = 0;
        for (CommunityPostImage image : kept) {
            image.setId(null);
            image.setSortOrder(order++);
            imageMapper.insert(image);
        }
        for (int i = 0; i < newBytes.size(); i++) {
            CommunityPostImage image = new CommunityPostImage();
            image.setPostId(id);
            image.setSortOrder(order++);
            image.setContentType(newTypes.get(i));
            image.setImageData(newBytes.get(i));
            imageMapper.insert(image);
        }

        post.setImageCount(order);
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        log.info("编辑社区动态配图: postId={}, userId={}, 保留={}, 新增={}", id, userId, kept.size(), newBytes.size());
        return Response.success("已更新配图", order);
    }

    /** 删除自己的动态，连带清理图片 / 点赞 / 评论 */
    @DeleteMapping("/posts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Response<Void> delete(@PathVariable Integer id, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        CommunityPost post = postMapper.selectById(id);
        if (post == null) {
            return Response.error(404, "动态不存在或已删除");
        }
        if (!userId.equals(post.getUserId())) {
            return Response.error(403, "只能删除自己发布的动态");
        }
        postMapper.deleteById(id);
        imageMapper.delete(new LambdaQueryWrapper<CommunityPostImage>().eq(CommunityPostImage::getPostId, id));
        likeMapper.delete(new LambdaQueryWrapper<CommunityPostLike>().eq(CommunityPostLike::getPostId, id));
        commentMapper.delete(new LambdaQueryWrapper<CommunityPostComment>().eq(CommunityPostComment::getPostId, id));
        log.info("删除社区动态: postId={}, userId={}", id, userId);
        return Response.success("已删除", null);
    }

    // ---------------- 点赞 ----------------

    /** 点赞：重复点赞按幂等处理，靠 uk_post_user 唯一索引互斥 */
    @PostMapping("/posts/{id}/like")
    public Response<LikeResult> like(@PathVariable Integer id, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        if (!canView(postMapper.selectById(id), userId)) {
            return Response.error(404, "动态不存在或已删除");
        }
        boolean added = false;
        try {
            CommunityPostLike like = new CommunityPostLike();
            like.setPostId(id);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            likeMapper.insert(like);
            added = true;
        } catch (DuplicateKeyException e) {
            // 已经点过赞，保持幂等，不再累加计数
        }
        if (added) {
            postMapper.adjustLikeCount(id, 1);
        }
        CommunityPost latest = postMapper.selectById(id);
        int likeCount = latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0;
        return Response.success(new LikeResult(true, likeCount));
    }

    /** 取消点赞 */
    @DeleteMapping("/posts/{id}/like")
    public Response<LikeResult> unlike(@PathVariable Integer id, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        int deleted = likeMapper.delete(new LambdaQueryWrapper<CommunityPostLike>()
                .eq(CommunityPostLike::getPostId, id)
                .eq(CommunityPostLike::getUserId, userId));
        if (deleted > 0) {
            postMapper.adjustLikeCount(id, -1);
        }
        CommunityPost latest = postMapper.selectById(id);
        int likeCount = latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0;
        return Response.success(new LikeResult(false, likeCount));
    }

    // ---------------- 评论 ----------------

    /** 评论列表：按时间正序，翻页往更早的看 */
    @GetMapping("/posts/{id}/comments")
    public Response<List<CommentItem>> comments(@PathVariable Integer id,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication authentication) {
        if (!canView(postMapper.selectById(id), AuthUtils.resolveUserId(authentication))) {
            return Response.error(404, "动态不存在或已删除");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageHelper.startPage(safePage, safeSize);
        List<CommunityPostComment> comments = commentMapper.selectList(new LambdaQueryWrapper<CommunityPostComment>()
                .eq(CommunityPostComment::getPostId, id)
                .orderByAsc(CommunityPostComment::getId));

        Map<Integer, User> authorCache = new HashMap<>();
        List<CommentItem> result = new ArrayList<>();
        for (CommunityPostComment comment : comments) {
            CommentItem item = new CommentItem();
            item.setId(comment.getId());
            item.setUserId(comment.getUserId());
            item.setContent(comment.getContent());
            item.setCreatedAt(comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : "");
            User author = findAuthor(comment.getUserId(), authorCache);
            item.setUserName(displayName(author));
            item.setHasAvatar(hasAvatar(author));
            result.add(item);
        }
        return Response.success(result);
    }

    /** 发表评论 */
    @PostMapping("/posts/{id}/comments")
    public Response<Integer> addComment(@PathVariable Integer id,
                                        @RequestBody(required = false) Map<String, Object> payload,
                                        Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        if (!canView(postMapper.selectById(id), userId)) {
            return Response.error(404, "动态不存在或已删除");
        }
        Object raw = payload == null ? null : payload.get("content");
        String content = raw == null ? "" : raw.toString().trim();
        if (content.isEmpty()) {
            return Response.error(400, "评论内容不能为空");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            content = content.substring(0, MAX_COMMENT_LENGTH);
        }

        CommunityPostComment comment = new CommunityPostComment();
        comment.setPostId(id);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        postMapper.adjustCommentCount(id, 1);
        log.info("社区评论: postId={}, commentId={}, userId={}", id, comment.getId(), userId);
        return Response.success(comment.getId());
    }

    // ---------------- 配图读取 ----------------

    /** 动态配图：按排序序号取二进制，未登录也可访问 */
    @GetMapping("/posts/{postId}/images/{index}")
    public ResponseEntity<byte[]> image(@PathVariable Integer postId, @PathVariable Integer index) {
        List<CommunityPostImage> images = imageMapper.selectList(new LambdaQueryWrapper<CommunityPostImage>()
                .eq(CommunityPostImage::getPostId, postId)
                .eq(CommunityPostImage::getSortOrder, index));
        if (images.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CommunityPostImage image = images.get(0);
        byte[] data = image.getImageData();
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String contentType = image.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "image/jpeg";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentLength(data.length);
        // 图片内容一旦写入就不再变化，允许客户端长时间缓存
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    // ---------------- 内部工具 ----------------

    /** 可见范围只接受 0 / 1，其它值一律按公开处理 */
    private int normalizeVisibility(int visibility) {
        return visibility == VISIBILITY_PRIVATE ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
    }

    /** 私密动态只有作者本人能看，其他人一律当作不存在 */
    private boolean canView(CommunityPost post, Integer viewerId) {
        if (post == null) {
            return false;
        }
        int visibility = post.getVisibility() == null ? VISIBILITY_PUBLIC : post.getVisibility();
        return visibility == VISIBILITY_PUBLIC || (viewerId != null && viewerId.equals(post.getUserId()));
    }

    private User findAuthor(Integer userId, Map<Integer, User> cache) {
        if (userId == null) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        User user = userService.findById(userId);
        cache.put(userId, user);
        return user;
    }

    private String displayName(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isEmpty()) {
            return "匿名用户";
        }
        return user.getUsername();
    }

    private boolean hasAvatar(User user) {
        return user != null && user.getAvatarData() != null && user.getAvatarData().length > 0;
    }

    // ---------------- 视图对象 ----------------

    public static class PostItem {
        private Integer id;
        private Integer userId;
        private String userName;
        private Boolean hasAvatar;
        private String content;
        private Integer imageCount;
        private Integer likeCount;
        private Integer commentCount;
        private Boolean liked;
        private Boolean mine;
        private Integer visibility;
        private String createdAt;
        private String updatedAt;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Boolean getHasAvatar() { return hasAvatar; }
        public void setHasAvatar(Boolean hasAvatar) { this.hasAvatar = hasAvatar; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getImageCount() { return imageCount; }
        public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }
        public Integer getLikeCount() { return likeCount; }
        public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
        public Integer getCommentCount() { return commentCount; }
        public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
        public Boolean getLiked() { return liked; }
        public void setLiked(Boolean liked) { this.liked = liked; }
        public Boolean getMine() { return mine; }
        public void setMine(Boolean mine) { this.mine = mine; }
        public Integer getVisibility() { return visibility; }
        public void setVisibility(Integer visibility) { this.visibility = visibility; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CommentItem {
        private Integer id;
        private Integer userId;
        private String userName;
        private Boolean hasAvatar;
        private String content;
        private String createdAt;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Boolean getHasAvatar() { return hasAvatar; }
        public void setHasAvatar(Boolean hasAvatar) { this.hasAvatar = hasAvatar; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class LikeResult {
        private Boolean liked;
        private Integer likeCount;

        public LikeResult() {
        }

        public LikeResult(Boolean liked, Integer likeCount) {
            this.liked = liked;
            this.likeCount = likeCount;
        }

        public Boolean getLiked() { return liked; }
        public void setLiked(Boolean liked) { this.liked = liked; }
        public Integer getLikeCount() { return likeCount; }
        public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    }
}
