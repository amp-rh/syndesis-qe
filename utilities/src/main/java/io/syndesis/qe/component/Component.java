package io.syndesis.qe.component;

import lombok.Getter;

@Getter
public enum Component {
    DB("syndesis-db"),
    OAUTH("syndesis-oauthproxy"),
    PROMETHEUS("syndesis-prometheus"),
    SERVER("syndesis-server"),
    UI("syndesis-ui"),
    META("syndesis-meta"),
    OPERATOR("syndesis-operator"),
    FUSE_ONLINE_OPERATOR("fuse-online-operator"),
    TODO("todo"),
    JAEGER("syndesis-jaeger");

    private final String name;

    Component(String name) {
        this.name = name;
    }
}
