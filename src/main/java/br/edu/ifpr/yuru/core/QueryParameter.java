package br.edu.ifpr.yuru.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class QueryParameter {

    final private String field;
    final private String operator;
    final private String value;

}
