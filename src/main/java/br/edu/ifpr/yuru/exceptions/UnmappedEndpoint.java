package br.edu.ifpr.yuru.exceptions;

public class UnmappedEndpoint extends Exception {

    public UnmappedEndpoint() {
        super("The requested endpoint mapping could not be found");
    }

}
