package com.saroga.timewarp.application.factory;

import com.saroga.timewarp.domain.model.enums.TargetGroup;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TargetingFactory {

    public List<TargetingRequirement> createTargeting(String targetsRecipe) {
        List<TargetingRequirement> targetsList = new ArrayList<>();

        if (targetsRecipe == null || targetsRecipe.isBlank() || targetsRecipe.equalsIgnoreCase("NONE")) {
            return targetsList;
        }

        // Cortamos por el punto y coma. Ej: ["2:ALLY", "1:ENEMY"]
        String[] requirements = targetsRecipe.split(";");

        for (String req : requirements) {
            String[] parts = req.split(":");
            if (parts.length == 2) {
                try {
                    int count = Integer.parseInt(parts[0].trim());
                    TargetGroup group = TargetGroup.valueOf(parts[1].trim().toUpperCase());

                    targetsList.add(new TargetingRequirement(count, group));
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parseando target de BBDD: " + req);
                }
            }
        }
        return targetsList;
    }
}