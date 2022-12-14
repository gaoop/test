package com.gp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gp.constants.SystemConstants;
import com.gp.domain.ResponseResult;
import com.gp.domain.dto.ArticleDto;
import com.gp.domain.dto.addArticleDto;
import com.gp.domain.entity.Article;
import com.gp.domain.entity.ArticleTag;
import com.gp.domain.entity.Category;
import com.gp.domain.vo.*;
import com.gp.enums.AppHttpCodeEnum;
import com.gp.mapper.ArticleMapper;
import com.gp.service.ArticleTagService;
import com.gp.service.ArtivleService;
import com.gp.service.CategoryService;
import com.gp.utils.BeanCopyUtils;
import com.gp.utils.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArtivleService {
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private ArticleTagService articleTagService;
    @Autowired
    private ArticleMapper articleMapper;


    @Override
    public ResponseResult hotArticleList() {
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        queryWrapper.orderByDesc(Article::getViewCount);
        Page<Article> page = new Page<>(1, 10);
        page(page, queryWrapper);
        List<Article> records = page.getRecords();

        List<HotArticleVo> hotArticleVos = BeanCopyUtils.copyBeanList(records, HotArticleVo.class);

        return ResponseResult.okResult(hotArticleVos);
    }

    @Override
    public ResponseResult articleList(Integer pageNum, Integer pageSize, Long cattegoryId) {
        //????????????
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        //?????????cattegoryIId????????????????????????????????????
        queryWrapper.eq(Objects.nonNull(cattegoryId) && cattegoryId > 0, Article::getCategoryId, cattegoryId);
        //????????????????????????
        queryWrapper.eq(Article::getStatus, SystemConstants.ARTICLE_STATUS_NORMAL);
        //???isTop??????????????????
        queryWrapper.orderByDesc(Article::getIsTop);
        //????????????
        Page<Article> page = new Page<>(pageNum, pageSize);
        page(page, queryWrapper);
        //??????????????????
        List<Article> records = page.getRecords();
        //articleID?????????articleName????????????
        records.stream()
                .map(article -> article.setCategoryName(categoryService.getById(article.getCategoryId()).getName()))
                .collect(Collectors.toList());
//        for (Article article:records){
//            Category byId = categoryService.getById(article.getCategoryId());
//            article.setCategoryName(byId.getName());
//        }
        //??????????????????
        List<ArticleListVo> articleListVos = BeanCopyUtils.copyBeanList(page.getRecords(), ArticleListVo.class);

        PageVo pageVo = new PageVo(articleListVos, page.getTotal());
        return ResponseResult.okResult(pageVo);
    }

    @Override
    public ResponseResult getArticleDetail(Long id) {
        //??????id????????????
        Article byId = getById(id);
        //?????????vo
        ArticleDetailVo articleDetailVo = BeanCopyUtils.copyBean(byId, ArticleDetailVo.class);
        //????????????id???????????????
        Long categoryId = articleDetailVo.getCategoryId();
        Category byId1 = categoryService.getById(categoryId);
        if (byId1 != null) {
            articleDetailVo.setCategoryName(byId1.getName());
        }
        //??????????????????
        return ResponseResult.okResult(articleDetailVo);
    }

    @Override
    public ResponseResult updateViewCount(Long id) {
        redisCache.incrementCacheMapValue("ViewCount", id.toString(), 1);
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult add(addArticleDto article) {
        Article article1 = BeanCopyUtils.copyBean(article, Article.class);
        save(article1);

        List<ArticleTag> collect = article.getTags().stream()
                .map(tagId -> new ArticleTag(article1.getId(), tagId))
                .collect(Collectors.toList());

        articleTagService.saveBatch(collect);
        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getList(Integer pageNum, Integer pageSize, String title, String summary) {
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        Page<Article> page = new Page<>(pageNum, pageSize);
        if (StringUtils.hasText(title)) {
            queryWrapper.like(Article::getTitle, title);
        }
        if (StringUtils.hasText(summary)){
            queryWrapper.like(Article::getSummary,summary);
        }
        page(page,queryWrapper);
        List<Article> articleList=page.getRecords();
        List<ArticleVo> articleVos = BeanCopyUtils.copyBeanList(articleList, ArticleVo.class);
        PageVo articleVo = new PageVo(articleVos,page.getTotal());
        return ResponseResult.okResult(articleVo);
    }

    @Override
    public ResponseResult deleteArticle(Long id) {
        if (Objects.isNull(id)){
            return ResponseResult.errorResult(AppHttpCodeEnum.DELETE_ERROR);
        }
        int i = articleMapper.deleteById(id);
        if (i>0){
            return ResponseResult.okResult();
        }
        return ResponseResult.errorResult(AppHttpCodeEnum.DELETE_ERROR);
    }

    @Override
    public ArticleVo getInfo(Long id) {
        Article byId = getById(id);
        LambdaQueryWrapper<ArticleTag> articleTagLambdaQueryWrapper=new LambdaQueryWrapper<>();
        articleTagLambdaQueryWrapper.eq(ArticleTag::getArticleId,byId.getId());
        List<ArticleTag> list = articleTagService.list(articleTagLambdaQueryWrapper);
        List<Long> collect = list.stream().map(articleTag -> articleTag.getTagId()).collect(Collectors.toList());
        ArticleVo articleVo = BeanCopyUtils.copyBean(byId, ArticleVo.class);
        articleVo.setTags(collect);
        return articleVo;
    }

    @Override
    public void edit(ArticleDto article) {
        Article article1 = BeanCopyUtils.copyBean(article, Article.class);
        //??????????????????
        updateById(article1);
        //??????????????? ????????????????????????
        LambdaQueryWrapper<ArticleTag> articleTagLambdaQueryWrapper = new LambdaQueryWrapper<>();
        articleTagLambdaQueryWrapper.eq(ArticleTag::getArticleId,article.getId());
        articleTagService.remove(articleTagLambdaQueryWrapper);
        //??????????????????????????????????????????
        List<ArticleTag> articleTags = article.getTags().stream()
                .map(tagId -> new ArticleTag(article.getId(), tagId))
                .collect(Collectors.toList());
        articleTagService.saveBatch(articleTags);
    }


}