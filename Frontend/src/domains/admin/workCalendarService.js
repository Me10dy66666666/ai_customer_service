import http from '@/core/axios'

export function getCalendars() {
  return http.get('/api/admin/work-calendar')
}

export function getCalendar(id) {
  return http.get(`/api/admin/work-calendar/${id}`)
}

export function updateCalendar(id, data) {
  return http.put(`/api/admin/work-calendar/${id}`, data)
}

export function getSpecialDates(calendarId) {
  return http.get(`/api/admin/work-calendar/${calendarId}/special-dates`)
}

export function addSpecialDate(calendarId, data) {
  return http.post(`/api/admin/work-calendar/${calendarId}/special-dates`, data)
}

export function deleteSpecialDate(calendarId, dateId) {
  return http.delete(`/api/admin/work-calendar/${calendarId}/special-dates/${dateId}`)
}
