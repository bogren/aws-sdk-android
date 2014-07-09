/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 * 
 *  http://aws.amazon.com/apache2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.ec2.model;

/**
 * Vpn State
 */
public enum VpnState {
    
    Pending("pending"),
    Available("available"),
    Deleting("deleting"),
    Deleted("deleted");

    private String value;

    private VpnState(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * Use this in place of valueOf.
     *
     * @param value
     *            real value
     * @return VpnState corresponding to the value
     */
    public static VpnState fromValue(String value) {
        if (value == null || "".equals(value)) {
            throw new IllegalArgumentException("Value cannot be null or empty!");
        
        } else if ("pending".equals(value)) {
            return VpnState.Pending;
        } else if ("available".equals(value)) {
            return VpnState.Available;
        } else if ("deleting".equals(value)) {
            return VpnState.Deleting;
        } else if ("deleted".equals(value)) {
            return VpnState.Deleted;
        } else {
            throw new IllegalArgumentException("Cannot create enum from " + value + " value!");
        }
    }
}
    