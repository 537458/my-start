package com.petshop.util;

import android.text.TextUtils;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class ValidationUtil {

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final String PASSWORD_REGEX = "^(?=.*[a-zA-Z])(?=.*\\d).{6,20}$";
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]{3,20}$";

    public static boolean isEmpty(String value) {
        return TextUtils.isEmpty(value) || value.trim().isEmpty();
    }

    public static boolean isEmpty(EditText editText) {
        return isEmpty(editText.getText().toString());
    }

    public static boolean isEmail(String email) {
        return !isEmpty(email) && Pattern.matches(EMAIL_REGEX, email);
    }

    public static boolean isPhone(String phone) {
        return !isEmpty(phone) && Pattern.matches(PHONE_REGEX, phone);
    }

    public static boolean isPassword(String password) {
        return !isEmpty(password) && password.length() >= 6 && password.length() <= 20;
    }

    public static boolean isUsername(String username) {
        return !isEmpty(username) && Pattern.matches(USERNAME_REGEX, username);
    }

    public static boolean isStrongPassword(String password) {
        return !isEmpty(password) && Pattern.matches(PASSWORD_REGEX, password);
    }

    public static boolean isNumber(String value) {
        if (isEmpty(value)) return false;
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isPositiveNumber(String value) {
        if (!isNumber(value)) return false;
        return Integer.parseInt(value) > 0;
    }

    public static boolean isPositiveNumber(String value, int min, int max) {
        if (!isNumber(value)) return false;
        int num = Integer.parseInt(value);
        return num >= min && num <= max;
    }

    public static boolean isFloat(String value) {
        if (isEmpty(value)) return false;
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isPositiveFloat(String value) {
        if (!isFloat(value)) return false;
        return Float.parseFloat(value) > 0;
    }

    public static boolean isPositiveFloat(String value, float min, float max) {
        if (!isFloat(value)) return false;
        float num = Float.parseFloat(value);
        return num >= min && num <= max;
    }

    public static void setEditTextError(TextInputLayout textInputLayout, String error) {
        if (textInputLayout != null) {
            textInputLayout.setError(error);
            textInputLayout.setErrorEnabled(true);
        }
    }

    public static void clearEditTextError(TextInputLayout textInputLayout) {
        if (textInputLayout != null) {
            textInputLayout.setError(null);
            textInputLayout.setErrorEnabled(false);
        }
    }

    public static boolean validateRequired(TextInputLayout textInputLayout, String fieldName) {
        String value = textInputLayout.getEditText() != null ? 
            textInputLayout.getEditText().getText().toString().trim() : "";
        
        if (isEmpty(value)) {
            setEditTextError(textInputLayout, "请输入" + fieldName);
            return false;
        }
        
        clearEditTextError(textInputLayout);
        return true;
    }

    public static boolean validateUsername(TextInputLayout textInputLayout) {
        String value = textInputLayout.getEditText() != null ? 
            textInputLayout.getEditText().getText().toString().trim() : "";
        
        if (isEmpty(value)) {
            setEditTextError(textInputLayout, "请输入用户名");
            return false;
        }
        
        if (!isUsername(value)) {
            setEditTextError(textInputLayout, "用户名长度3-20位，只能包含字母、数字和下划线");
            return false;
        }
        
        clearEditTextError(textInputLayout);
        return true;
    }

    public static boolean validateEmail(TextInputLayout textInputLayout) {
        String value = textInputLayout.getEditText() != null ? 
            textInputLayout.getEditText().getText().toString().trim() : "";
        
        if (isEmpty(value)) {
            setEditTextError(textInputLayout, "请输入邮箱");
            return false;
        }
        
        if (!isEmail(value)) {
            setEditTextError(textInputLayout, "请输入有效的邮箱地址");
            return false;
        }
        
        clearEditTextError(textInputLayout);
        return true;
    }

    public static boolean validatePhone(TextInputLayout textInputLayout) {
        String value = textInputLayout.getEditText() != null ? 
            textInputLayout.getEditText().getText().toString().trim() : "";
        
        if (isEmpty(value)) {
            setEditTextError(textInputLayout, "请输入手机号");
            return false;
        }
        
        if (!isPhone(value)) {
            setEditTextError(textInputLayout, "请输入有效的手机号");
            return false;
        }
        
        clearEditTextError(textInputLayout);
        return true;
    }

    public static boolean validatePassword(TextInputLayout textInputLayout) {
        String value = textInputLayout.getEditText() != null ? 
            textInputLayout.getEditText().getText().toString() : "";
        
        if (isEmpty(value)) {
            setEditTextError(textInputLayout, "请输入密码");
            return false;
        }
        
        if (!isPassword(value)) {
            setEditTextError(textInputLayout, "密码长度为6-20位");
            return false;
        }
        
        clearEditTextError(textInputLayout);
        return true;
    }

    public static boolean validateConfirmPassword(TextInputLayout passwordLayout, 
                                                  TextInputLayout confirmPasswordLayout) {
        String password = passwordLayout.getEditText() != null ? 
            passwordLayout.getEditText().getText().toString() : "";
        String confirmPassword = confirmPasswordLayout.getEditText() != null ? 
            confirmPasswordLayout.getEditText().getText().toString() : "";
        
        if (isEmpty(confirmPassword)) {
            setEditTextError(confirmPasswordLayout, "请确认密码");
            return false;
        }
        
        if (!password.equals(confirmPassword)) {
            setEditTextError(confirmPasswordLayout, "两次输入的密码不一致");
            return false;
        }
        
        clearEditTextError(confirmPasswordLayout);
        return true;
    }
}
