package ru.maklas.mrudp2;

/**
 * Package which is used to respond on ConnectionRequest.
 * @param <T> will be send with response
 */
public class Response<T> {

    final boolean accept;
    T responseData;

    /**
     * Respond with success flag
     * @param data Your response data
     */
    public static <T> Response<T> accept(T data){
        return new Response<T>(true, data);
    }

    /**
     * Respond with refuse flag
     * @param data Your response data
     */
    public static <T> Response<T> refuse(T data){
        return new Response<T>(false, data);
    }

    private Response(boolean accept, T responseData) {
        this.accept = accept;
        this.responseData = responseData;
    }

    public boolean accepted() {
        return accept;
    }

    public T getResponseData() {
        return responseData;
    }

    public void setResponseData(T responseData) {
        this.responseData = responseData;
    }
}
