package com.choom.domain.originaldance.entity;

import com.choom.domain.bookmark.entity.Bookmark;
import com.choom.global.common.BaseTimeEntity;
import com.choom.domain.coordinate.entity.Coordinate;
import com.choom.domain.mydance.entity.MyDance;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@DynamicInsert
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OriginalDance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(length = 100)
    private String title;

    @NotNull
    @Column(length = 2083, unique = true)
    private String url;

    @Column(length = 2083, unique = true)
    private String videoPath;

    @Column(length = 2083, unique = true)
    private String thumbnailPath;

    @NotNull
    @Column
    @ColumnDefault("0")
    private int userCount;

    @NotNull
    @Column
    @ColumnDefault("0")
    private int status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COORDINATE_ID", unique = true)
    private Coordinate coordinate;

    @OneToMany(mappedBy = "originalDance", cascade = CascadeType.ALL)
    private List<Bookmark> bookmarkList = new ArrayList<>();

    @OneToMany(mappedBy = "originalDance", cascade = CascadeType.ALL)
    private List<MyDance> myDanceList = new ArrayList<>();
}