package com.mojang.realmsclient.exception;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsHttpException extends RuntimeException {
    public RealmsHttpException(String s, Exception e) {
        super(s, e);
    }
}
