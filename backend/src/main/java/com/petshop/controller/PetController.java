package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.common.Response;
import com.petshop.entity.Pet;
import com.petshop.mapper.PetMapper;
import com.petshop.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的宠物：档案的增删改查与头像读写。
 * 宠物属于当前登录用户，接口里一律按 userId 隔离。
 */
@RestController
@RequestMapping("/pets")
public class PetController {

    private static final Logger log = LoggerFactory.getLogger(PetController.class);

    private static final int MAX_NAME_LENGTH = 20;
    private static final int MAX_BREED_LENGTH = 30;
    private static final int MAX_NOTE_LENGTH = 100;
    /** 头像超过这个大小直接拒绝，客户端上传前已经压缩过 */
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    private static final String SPECIES_CAT = "cat";
    private static final String SPECIES_DOG = "dog";
    private static final String SPECIES_OTHER = "other";

    private static final String GENDER_MALE = "male";
    private static final String GENDER_FEMALE = "female";
    private static final String GENDER_UNKNOWN = "unknown";

    /** 大类 -> 常见品种，客户端做选择列表用 */
    private static final Map<String, List<String>> BREEDS = buildBreeds();

    @Autowired
    private PetMapper petMapper;

    private static Map<String, List<String>> buildBreeds() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(SPECIES_CAT, Arrays.asList(
                "中华田园猫", "狸花猫", "橘猫", "奶牛猫", "三花猫", "玄猫", "狮子猫",
                "英国短毛猫", "蓝白英短", "银渐层", "金渐层", "美国短毛猫",
                "布偶猫", "暹罗猫", "缅因猫", "波斯猫", "加菲猫", "金吉拉",
                "苏格兰折耳猫", "孟加拉豹猫", "无毛猫", "挪威森林猫", "德文卷毛猫",
                "阿比西尼亚猫", "伯曼猫", "东方短毛猫"));
        map.put(SPECIES_DOG, Arrays.asList(
                "中华田园犬", "比格犬", "金毛寻回犬", "拉布拉多", "柯基", "柴犬",
                "哈士奇", "阿拉斯加雪橇犬", "萨摩耶", "边境牧羊犬", "贵宾犬（泰迪）",
                "博美", "雪纳瑞", "法国斗牛犬", "英国斗牛犬", "比熊", "吉娃娃",
                "蝴蝶犬", "杜宾犬", "罗威纳", "德国牧羊犬", "巴哥犬", "约克夏",
                "马尔济斯", "大丹犬", "圣伯纳", "松狮", "秋田犬"));
        map.put(SPECIES_OTHER, Arrays.asList(
                "垂耳兔", "侏儒兔", "仓鼠", "金丝熊", "龙猫", "豚鼠", "刺猬",
                "玄凤鹦鹉", "虎皮鹦鹉", "巴西龟", "金鱼", "斗鱼", "蜥蜴", "其他小宠"));
        return map;
    }

    // ---------------- 查询 ----------------

    /** 我的宠物列表（不含头像二进制，只给 URL） */
    @GetMapping
    public Response<List<PetItem>> list(Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        List<Pet> pets = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .orderByAsc(Pet::getId));
        List<PetItem> result = new ArrayList<>();
        for (Pet pet : pets) {
            result.add(toItem(pet));
        }
        return Response.success(result);
    }

    /** 品种字典，key 是大类代码，value 是品种名列表 */
    @GetMapping("/breeds")
    public Response<Map<String, List<String>>> breeds() {
        return Response.success(BREEDS);
    }

    /** 宠物头像：直接返回二进制，客户端按 updatedAt 做缓存版本 */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Integer id) {
        Pet pet = petMapper.selectById(id);
        if (pet == null || pet.getAvatarData() == null || pet.getAvatarData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveContentType(pet.getAvatarType()));
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(pet.getAvatarData(), headers, HttpStatus.OK);
    }

    // ---------------- 新增 / 编辑 / 删除 ----------------

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<PetItem> create(@RequestParam("name") String name,
                                    @RequestParam(value = "species", required = false) String species,
                                    @RequestParam(value = "breed", required = false) String breed,
                                    @RequestParam(value = "gender", required = false) String gender,
                                    @RequestParam(value = "birthday", required = false) String birthday,
                                    @RequestParam(value = "weight", required = false) String weight,
                                    @RequestParam(value = "note", required = false) String note,
                                    @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                    Authentication authentication) throws IOException {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        String petName = trimToEmpty(name);
        if (petName.isEmpty()) {
            return Response.error(400, "给毛孩子起个名字吧");
        }
        if (petName.length() > MAX_NAME_LENGTH) {
            return Response.error(400, "名字最多 " + MAX_NAME_LENGTH + " 个字");
        }
        Double weightValue = parseWeight(weight);
        if (weightValue == null && !trimToEmpty(weight).isEmpty()) {
            return Response.error(400, "体重要填 0.1 ~ 200 之间的数字");
        }
        LocalDate birthdayValue = parseBirthday(birthday);
        if (birthdayValue == null && birthday != null && !birthday.trim().isEmpty()) {
            return Response.error(400, "生日格式不对，应为 2000-01-01");
        }
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_BYTES) {
                return Response.error(400, "图片太大了，换一张试试");
            }
            String contentType = avatar.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Response.error(400, "只能上传图片");
            }
        }

        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(petName);
        pet.setSpecies(normalizeSpecies(species));
        pet.setBreed(limit(trimToEmpty(breed), MAX_BREED_LENGTH));
        pet.setGender(normalizeGender(gender));
        pet.setBirthday(birthdayValue);
        pet.setWeight(weightValue);
        pet.setNote(limit(trimToEmpty(note), MAX_NOTE_LENGTH));
        pet.setCreatedAt(LocalDateTime.now());
        pet.setUpdatedAt(LocalDateTime.now());
        if (avatar != null && !avatar.isEmpty()) {
            pet.setAvatarData(avatar.getBytes());
            pet.setAvatarType(avatar.getContentType());
        }
        petMapper.insert(pet);
        log.info("添加宠物: petId={}, userId={}, species={}, breed={}",
                pet.getId(), userId, pet.getSpecies(), pet.getBreed());

        Pet saved = petMapper.selectById(pet.getId());
        return Response.success("添加成功", toItem(saved));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<PetItem> update(@PathVariable Integer id,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "species", required = false) String species,
                                    @RequestParam(value = "breed", required = false) String breed,
                                    @RequestParam(value = "gender", required = false) String gender,
                                    @RequestParam(value = "birthday", required = false) String birthday,
                                    @RequestParam(value = "weight", required = false) String weight,
                                    @RequestParam(value = "note", required = false) String note,
                                    @RequestParam(value = "removeAvatar", defaultValue = "false") boolean removeAvatar,
                                    @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                    Authentication authentication) throws IOException {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Pet pet = petMapper.selectById(id);
        if (pet == null || !userId.equals(pet.getUserId())) {
            return Response.error(404, "宠物不存在");
        }
        String petName = trimToEmpty(name);
        if (petName.isEmpty()) {
            return Response.error(400, "给毛孩子起个名字吧");
        }
        if (petName.length() > MAX_NAME_LENGTH) {
            return Response.error(400, "名字最多 " + MAX_NAME_LENGTH + " 个字");
        }
        Double weightValue = parseWeight(weight);
        if (weightValue == null && !trimToEmpty(weight).isEmpty()) {
            return Response.error(400, "体重要填 0.1 ~ 200 之间的数字");
        }
        LocalDate birthdayValue = parseBirthday(birthday);
        if (birthdayValue == null && birthday != null && !birthday.trim().isEmpty()) {
            return Response.error(400, "生日格式不对，应为 2000-01-01");
        }
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_BYTES) {
                return Response.error(400, "图片太大了，换一张试试");
            }
            String contentType = avatar.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Response.error(400, "只能上传图片");
            }
        }

        pet.setName(petName);
        pet.setSpecies(normalizeSpecies(species));
        pet.setBreed(limit(trimToEmpty(breed), MAX_BREED_LENGTH));
        pet.setGender(normalizeGender(gender));
        pet.setBirthday(birthdayValue);
        pet.setWeight(weightValue);
        pet.setNote(limit(trimToEmpty(note), MAX_NOTE_LENGTH));
        pet.setUpdatedAt(LocalDateTime.now());
        if (avatar != null && !avatar.isEmpty()) {
            pet.setAvatarData(avatar.getBytes());
            pet.setAvatarType(avatar.getContentType());
        } else if (removeAvatar) {
            pet.setAvatarData(null);
            pet.setAvatarType(null);
        }
        petMapper.updateById(pet);
        log.info("编辑宠物: petId={}, userId={}, removeAvatar={}", id, userId, removeAvatar);

        Pet saved = petMapper.selectById(id);
        return Response.success("已保存", toItem(saved));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Integer id, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        Pet pet = petMapper.selectById(id);
        if (pet == null || !userId.equals(pet.getUserId())) {
            return Response.error(404, "宠物不存在");
        }
        petMapper.deleteById(id);
        log.info("删除宠物: petId={}, userId={}", id, userId);
        return Response.success("已删除", null);
    }

    // ---------------- 工具方法 ----------------

    private PetItem toItem(Pet pet) {
        PetItem item = new PetItem();
        item.setId(pet.getId());
        item.setName(pet.getName());
        item.setSpecies(pet.getSpecies() == null ? SPECIES_OTHER : pet.getSpecies());
        item.setBreed(pet.getBreed());
        item.setGender(pet.getGender() == null ? GENDER_UNKNOWN : pet.getGender());
        item.setBirthday(pet.getBirthday() == null ? null : pet.getBirthday().toString());
        item.setWeight(pet.getWeight());
        item.setNote(pet.getNote());
        item.setHasAvatar(pet.getAvatarData() != null && pet.getAvatarData().length > 0);
        item.setUpdatedAt(pet.getUpdatedAt() == null ? "" : pet.getUpdatedAt().toString());
        return item;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String normalizeSpecies(String species) {
        String value = trimToEmpty(species).toLowerCase();
        if (SPECIES_CAT.equals(value) || SPECIES_DOG.equals(value)) {
            return value;
        }
        return SPECIES_OTHER;
    }

    private String normalizeGender(String gender) {
        String value = trimToEmpty(gender).toLowerCase();
        if (GENDER_MALE.equals(value) || GENDER_FEMALE.equals(value)) {
            return value;
        }
        return GENDER_UNKNOWN;
    }

    /** 允许留空（还不知道体重），填了就必须是 0.1 ~ 200 的数字 */
    private Double parseWeight(String weight) {
        String value = trimToEmpty(weight);
        if (value.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (parsed <= 0 || parsed > 200) {
                return null;
            }
            return Math.round(parsed * 100) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseBirthday(String birthday) {
        String value = trimToEmpty(birthday);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private MediaType resolveContentType(String stored) {
        if (stored == null || stored.isEmpty()) {
            return MediaType.IMAGE_JPEG;
        }
        try {
            return MediaType.parseMediaType(stored);
        } catch (Exception e) {
            return MediaType.IMAGE_JPEG;
        }
    }

    /** 返回给客户端的宠物档案 */
    public static class PetItem {
        private int id;
        private String name;
        /** cat / dog / other */
        private String species;
        private String breed;
        /** male / female / unknown */
        private String gender;
        /** yyyy-MM-dd，可为空 */
        private String birthday;
        private Double weight;
        private String note;
        private boolean hasAvatar;
        /** 最后更新时间，同时作为头像缓存版本号 */
        private String updatedAt;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSpecies() {
            return species;
        }

        public void setSpecies(String species) {
            this.species = species;
        }

        public String getBreed() {
            return breed;
        }

        public void setBreed(String breed) {
            this.breed = breed;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getBirthday() {
            return birthday;
        }

        public void setBirthday(String birthday) {
            this.birthday = birthday;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public boolean isHasAvatar() {
            return hasAvatar;
        }

        public void setHasAvatar(boolean hasAvatar) {
            this.hasAvatar = hasAvatar;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
