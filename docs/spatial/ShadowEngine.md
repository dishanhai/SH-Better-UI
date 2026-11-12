<!-- 作者：山海／dishanhai -->
# 阴影

`node.shadows(List<Shadow>)` 显式指定至多八层。传 `null` 恢复主题阴影，空列表关闭此节点的阴影。外层支持 `DROP`、`AMBIENT`、`CONTACT`、`DIRECTIONAL` 和 `GLOW`，`INNER` 在表面之后绘制。同一阶段保留列表顺序。

圆角矩形按象限选半径，每个半径限制在短边的一半。圆、椭圆和胶囊共享同一渲染器。椭圆采用归一化距离近似，轮廓命中使用相同的零等值线。高质量衰减使用高斯直边积分近似，低质量使用平滑阶跃。四边形内用导数计算边缘抗锯齿。

输入颜色为 straight ARGB。颜色增强在线性空间计算并编码回 sRGB，再按 Minecraft GUI 的 straight-alpha 混合约定合成。Shader 不可用时走三角化表面，阴影能力为 `DEGRADED`。

`Shadow.interpolate` 在相同类型之间插值偏移、模糊、扩展、透明度和颜色，颜色按端点的 alpha 加权。

## 凹陷输入框

```java
field.material(SpatialStyle.Material.SOFT_INSET);
field.shadows(List.of(new SpatialStyle.Shadow(SpatialStyle.ShadowType.INNER,
        0, 2, 3, 0, .55, 0xff090e19, true)));
```

## 自定义阴影轮廓

`SpatialUI.capability(SpatialUI.Capability.ARBITRARY_PATH_SHADOWS)` 可查询原生路径阴影。关闭 Spatial 或阴影设置时返回 `DISABLED`，所需着色器不可用时返回 `DEGRADED`。降级时保留节点表面并跳过路径阴影。

```java
var outline = List.of(
        new Geometry2.Point<Geometry2.Local>(0, 0),
        new Geometry2.Point<Geometry2.Local>(52, 0),
        new Geometry2.Point<Geometry2.Local>(30, 18),
        new Geometry2.Point<Geometry2.Local>(52, 36),
        new Geometry2.Point<Geometry2.Local>(0, 36));
node.shadowPath(new SpatialGeometry.ShadowPath(List.of(outline)));
node.elevation(SpatialStyle.Elevation.RAISED);
```

轮廓使用节点局部 GUI 坐标，自动连接首尾顶点。每个路径合计最多 64 条边，坐标范围为 ±32768，以单精度处理。允许凹轮廓、分离区域和嵌套孔洞，填充采用奇偶规则。每个轮廓至少有三个顶点，相邻边应形成转角，轮廓边界不能相交或接触。曲线由调用方展平为折线。

路径随节点变换，适用于主题阴影和显式阴影层。内阴影和 `ShadowClip.NODE` 以路径轮廓为遮罩。其他裁剪策略仍遵循场景的父级和视口边界。节点表面、命中测试和子节点裁剪继续使用节点形状。传入 `null` 恢复默认阴影轮廓。
