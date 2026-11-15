package com.taskscheduler.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 8)
    private String cep;

    private String street;
    private String neighborhood;
    private String city;

    @Column(length = 2)
    private String state;

    @Column(nullable = false, length = 20)
    private String number;

    private String complement;
}
