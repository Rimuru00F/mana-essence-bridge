package com.frostfirebloom.manaessencebridge;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Цветок, который умеет рассказать о своём состоянии: переваривает, отдыхает,
 * сколько маны дал за секунду. Строки считаются на сервере и уходят в Jade и TOP.
 */
public interface FlowerStatus {

    List<Component> statusLines();
}
