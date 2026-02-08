package com.example.visualduress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.InputFieldBackground
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import com.example.visualduress.ui.theme.AccentOrange
import com.example.visualduress.R


@Composable
fun PasswordDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DialogBackground,
                elevation = 8.dp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = stringResource(R.string.enter_password),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password Input Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.password_placeholder),
                                color = TextSecondary.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = TextPrimary,
                            cursorColor = TextPrimary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            placeholderColor = TextSecondary.copy(alpha = 0.6f)
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = TextPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Cancel Button
                        Button(
                            onClick = {
                                password = ""
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.White,
                                contentColor = Color(0xFF2C3E50)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.elevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Text(
                                stringResource(R.string.cancel_button),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // OK Button
                        Button(
                            onClick = {
                                onConfirm(password)
                                password = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = AccentOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.elevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 6.dp
                            )
                        ) {
                            Text(
                                stringResource(R.string.ok_button),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}