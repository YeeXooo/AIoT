package com.aiot.interfaces.rest;

import com.aiot.application.DriverApplicationService;
import com.aiot.domain.model.Driver;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverApplicationService driverService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new ParameterNamesModule());

        mockMvc = MockMvcBuilders.standaloneSetup(new DriverController(driverService))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new StringHttpMessageConverter()
                )
                .build();
    }

    @Test
    void listReturnsAllDriversWhenNameParamAbsent() throws Exception {
        Driver driver = Driver.create("张三", "13800138000");
        when(driverService.list(null)).thenReturn(List.of(driver));

        mockMvc.perform(get("/api/v1/driver/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("张三"))
                .andExpect(jsonPath("$[0].phone").value("13800138000"));
    }

    @Test
    void listReturnsFilteredDriversWhenNameProvided() throws Exception {
        Driver driver = Driver.create("张三", "13800138000");
        when(driverService.list("张")).thenReturn(List.of(driver));

        mockMvc.perform(get("/api/v1/driver/list").param("name", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("张三"))
                .andExpect(jsonPath("$[0].phone").value("13800138000"));
    }

    @Test
    void listReturnsEmptyWhenNoDrivers() throws Exception {
        when(driverService.list(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/driver/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listWhenServiceThrowsException() {
        when(driverService.list(null)).thenThrow(new RuntimeException("DB error"));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(get("/api/v1/driver/list"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void addCreatesNewDriverWhenDriverIdIsNull() throws Exception {
        doNothing().when(driverService).add(any(Driver.class));

        mockMvc.perform(post("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":null,\"name\":\"李四\",\"phone\":\"13900139000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("李四"))
                .andExpect(jsonPath("$.phone").value("13900139000"))
                .andExpect(jsonPath("$.driverId.id").isNotEmpty());

        verify(driverService).add(any(Driver.class));
    }

    @Test
    void addCreatesNewDriverWhenDriverIdIdIsNull() throws Exception {
        doNothing().when(driverService).add(any(Driver.class));

        mockMvc.perform(post("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":{\"id\":null},\"name\":\"王五\",\"phone\":\"13700137000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("王五"))
                .andExpect(jsonPath("$.phone").value("13700137000"))
                .andExpect(jsonPath("$.driverId.id").isNotEmpty());

        verify(driverService).add(any(Driver.class));
    }

    @Test
    void addCreatesNewDriverWhenDriverIdIsEmpty() throws Exception {
        doNothing().when(driverService).add(any(Driver.class));

        mockMvc.perform(post("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":{\"id\":\"\"},\"name\":\"赵六\",\"phone\":\"13600136000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("赵六"))
                .andExpect(jsonPath("$.phone").value("13600136000"))
                .andExpect(jsonPath("$.driverId.id").isNotEmpty());

        verify(driverService).add(any(Driver.class));
    }

    @Test
    void addUsesExistingDriverIdWhenProvided() throws Exception {
        doNothing().when(driverService).add(any(Driver.class));

        mockMvc.perform(post("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":{\"id\":\"existing-id\"},\"name\":\"孙七\",\"phone\":\"13500135000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId.id").value("existing-id"))
                .andExpect(jsonPath("$.name").value("孙七"))
                .andExpect(jsonPath("$.phone").value("13500135000"));

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverService).add(captor.capture());
        assertEquals("existing-id", captor.getValue().driverId().id());
        assertEquals("孙七", captor.getValue().name());
        assertEquals("13500135000", captor.getValue().phone());
    }

    @Test
    void addWhenServiceThrowsException() {
        doThrow(new RuntimeException("DB error")).when(driverService).add(any(Driver.class));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(post("/api/v1/driver")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"driverId\":{\"id\":\"d-001\"},\"name\":\"test\",\"phone\":\"12345678901\"}"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void addWithoutDriverIdFieldInBody() throws Exception {
        doNothing().when(driverService).add(any(Driver.class));

        mockMvc.perform(post("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"周八\",\"phone\":\"13400134000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("周八"))
                .andExpect(jsonPath("$.phone").value("13400134000"))
                .andExpect(jsonPath("$.driverId.id").isNotEmpty());

        verify(driverService).add(any(Driver.class));
    }

    @Test
    void updateDriverAndReturnsIt() throws Exception {
        doNothing().when(driverService).update(any(Driver.class));

        mockMvc.perform(put("/api/v1/driver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":{\"id\":\"d-001\"},\"name\":\"updated\",\"phone\":\"99900001111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId.id").value("d-001"))
                .andExpect(jsonPath("$.name").value("updated"))
                .andExpect(jsonPath("$.phone").value("99900001111"));

        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverService).update(captor.capture());
        assertEquals("d-001", captor.getValue().driverId().id());
        assertEquals("updated", captor.getValue().name());
        assertEquals("99900001111", captor.getValue().phone());
    }

    @Test
    void updateWhenServiceThrowsException() {
        doThrow(new RuntimeException("DB error")).when(driverService).update(any(Driver.class));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(put("/api/v1/driver")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"driverId\":{\"id\":\"d-001\"},\"name\":\"test\",\"phone\":\"12345678901\"}"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(driverService).delete("d-001");

        mockMvc.perform(delete("/api/v1/driver/{id}", "d-001"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(driverService).delete("d-001");
    }

    @Test
    void deleteWhenServiceThrowsException() {
        doThrow(new RuntimeException("DB error")).when(driverService).delete("d-001");

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(delete("/api/v1/driver/{id}", "d-001"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }
}
