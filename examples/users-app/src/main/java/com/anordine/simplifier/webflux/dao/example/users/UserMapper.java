package com.anordine.simplifier.webflux.dao.example.users;

final class UserMapper {

    private UserMapper() {
    }

    static UserEntity toNewEntity(UserRequest request) {
        UserEntity user = new UserEntity();
        apply(user, request);
        return user;
    }

    static UserEntity toAssignedIdEntity(UserRequest request) {
        UserEntity user = new UserEntity();
        if (request.id() != null) {
            user.setId(request.id());
        }
        apply(user, request);
        return user;
    }

    static void apply(UserEntity user, UserRequest request) {
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setStatus(request.status());
        user.setCity(request.city());
    }
}
