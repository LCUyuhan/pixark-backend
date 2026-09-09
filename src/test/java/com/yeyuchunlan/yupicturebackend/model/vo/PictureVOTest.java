package com.yeyuchunlan.yupicturebackend.model.vo;

import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PictureVO} 单元测试：覆盖实体与 VO 之间的双向转换。
 */
class PictureVOTest {

    @Test
    void objToVo_null_returnsNull() {
        assertNull(PictureVO.objToVo(null));
    }

    @Test
    void objToVo_withTags_convertsJsonToStringList() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setName("图片");
        picture.setTags("[\"tag1\",\"tag2\"]");

        PictureVO vo = PictureVO.objToVo(picture);

        assertEquals(1L, vo.getId());
        assertEquals("图片", vo.getName());
        assertEquals(Arrays.asList("tag1", "tag2"), vo.getTags());
    }

    @Test
    void objToVo_nullTags_returnsEmptyList() {
        Picture picture = new Picture();
        picture.setTags(null);
        assertTrue(PictureVO.objToVo(picture).getTags().isEmpty());
    }

    @Test
    void voToObj_null_returnsNull() {
        assertNull(PictureVO.voToObj(null));
    }

    @Test
    void voToObj_withTags_convertsStringListToJson() {
        PictureVO vo = new PictureVO();
        vo.setId(1L);
        vo.setName("图片");
        vo.setTags(Arrays.asList("a", "b"));

        Picture picture = PictureVO.voToObj(vo);

        assertEquals(1L, picture.getId());
        assertEquals("图片", picture.getName());
        assertEquals("[\"a\",\"b\"]", picture.getTags());
    }
}