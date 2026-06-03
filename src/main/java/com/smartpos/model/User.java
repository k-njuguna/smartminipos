package com.smartpos.model;

import com.smartpos.model.enums.Role;

public record User(long id, String username, String password, Role role) {
}
