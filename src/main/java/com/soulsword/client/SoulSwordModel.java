package com.soulsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.soulsword.SoulSwordMod;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * Dark-abyssal greatsword. Origin is the middle of the grip; the blade extends towards -Y (up once flipped).
 * The "veins" and the guard gem are part of the same mesh, so the glow texture lights exactly those cubes.
 */
public class SoulSwordModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(SoulSwordMod.id("soul_sword"), "main");
    private final ModelPart root;

    public SoulSwordModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -23.0F, -1.0F, 4.0F, 18.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("tip_a", CubeListBuilder.create().texOffs(14, 0).addBox(-1.5F, -26.0F, -1.0F, 3.0F, 3.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("tip_b", CubeListBuilder.create().texOffs(26, 0).addBox(-0.5F, -28.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("guard", CubeListBuilder.create().texOffs(0, 22).addBox(-5.0F, -5.0F, -2.0F, 10.0F, 2.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(30, 22).addBox(-7.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(30, 28).addBox(5.0F, -7.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("grip", CubeListBuilder.create().texOffs(0, 30).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("pommel", CubeListBuilder.create().texOffs(10, 30).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.ZERO);
        // glowing parts (poke slightly out of the blade so they are visible from both faces)
        root.addOrReplaceChild("veins", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -22.0F, -1.5F, 1.0F, 16.0F, 3.0F), PartPose.ZERO);
        root.addOrReplaceChild("gem", CubeListBuilder.create().texOffs(0, 40).addBox(-1.5F, -5.5F, -2.5F, 3.0F, 3.0F, 5.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
